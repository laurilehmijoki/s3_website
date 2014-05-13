package s3.website.model

import com.amazonaws.services.s3.model.S3ObjectSummary
import java.io._
import scala.util.Try
import s3.website.model.Encoding._
import org.apache.commons.codec.digest.DigestUtils
import java.util.zip.GZIPOutputStream
import org.apache.commons.io.IOUtils
import org.apache.tika.Tika
import s3.website.Ruby._
import s3.website._
import s3.website.model.Encoding.Gzip
import scala.util.Failure
import scala.Some
import scala.util.Success
import s3.website.model.Encoding.Zopfli

object Encoding {

  val defaultGzipExtensions = ".html" :: ".css" :: ".js" :: ".txt" :: Nil

  case class Gzip()
  case class Zopfli()

  def encodingOnS3(path: String)(implicit site: Site): Option[Either[Gzip, Zopfli]] =
    site.config.gzip.flatMap { (gzipSetting: Either[Boolean, Seq[String]]) =>
      val shouldZipThisFile = gzipSetting.fold(
        shouldGzip => defaultGzipExtensions exists path.endsWith,
        fileExtensions => fileExtensions exists path.endsWith
      )
      if (shouldZipThisFile && site.config.gzip_zopfli.isDefined)
        Some(Right(Zopfli()))
      else if (shouldZipThisFile)
        Some(Left(Gzip()))
      else
        None
    }

  type MD5 = String
}

sealed trait S3KeyProvider {
  def s3Key: String
}

trait UploadTypeResolved {
  def uploadType: UploadType
}

sealed trait UploadType // Sealed, so that we can avoid inexhaustive pattern matches more easily

case object NewFile extends UploadType
case object Update extends UploadType

case class LocalFile(
  s3Key: String,
  originalFile: File,
  encodingOnS3: Option[Either[Gzip, Zopfli]]
) extends S3KeyProvider {

  // May throw an exception, so remember to call this in a Try or Future monad
  lazy val length = uploadFile.length()

  /**
   * This is the file we should upload, because it contains the potentially gzipped contents of the original file.
   *
   * May throw an exception, so remember to call this in a Try or Future monad
   */
  lazy val uploadFile: File = encodingOnS3
    .fold(originalFile)(algorithm => {
    val tempFile = File.createTempFile(originalFile.getName, "gzip")
    tempFile.deleteOnExit()
    using(new GZIPOutputStream(new FileOutputStream(tempFile))) { stream =>
      IOUtils.copy(fis(originalFile), stream)
    }
    tempFile
  })

  /**
   * May throw an exception, so remember to call this in a Try or Future monad
   */
  lazy val md5 = using(fis(uploadFile)) { inputStream =>
    DigestUtils.md5Hex(inputStream)
  }

  private[this] def fis(file: File): InputStream = new FileInputStream(file)
  private[this] def using[T <: Closeable, R](cl: T)(f: (T) => R): R = try f(cl) finally cl.close()
}

object LocalFile {
  def toUpload(localFile: LocalFile)(implicit config: Config): Either[ErrorReport, Upload] = Try {
    Upload(
      s3Key = localFile.s3Key,
      essence = Right(
        UploadBody(
          md5 = localFile.md5,
          contentEncoding = localFile.encodingOnS3.map(_ => "gzip"),
          contentLength = localFile.length,
          maxAge = resolveMaxAge(localFile),
          contentType = resolveContentType(localFile.originalFile),
          openInputStream = () => new FileInputStream(localFile.uploadFile)
        )
      )
    )
  } match {
    case Success(upload) => Right(upload)
    case Failure(error) => Left(IOError(error))
  }

  lazy val tika = new Tika()

  def resolveContentType(file: File) = {
    val mimeType = tika.detect(file)
    if (mimeType.startsWith("text/") || mimeType == "application/json")
      mimeType + "; charset=utf-8"
    else
      mimeType
  }

  def resolveMaxAge(localFile: LocalFile)(implicit config: Config): Option[Int] = {
    type GlobsMap = Map[String, Int]
    config.max_age.flatMap { (intOrGlobs: Either[Int, GlobsMap]) =>
      type GlobsSeq = Seq[(String, Int)]
      def respectMostSpecific(globs: GlobsMap): GlobsSeq = globs.toSeq.sortBy(_._1.length).reverse
      intOrGlobs
        .right.map(respectMostSpecific)
        .fold(
          (seconds: Int) => Some(seconds),
          (globs: GlobsSeq) =>
            globs.find { globAndInt =>
              (rubyRuntime evalScriptlet s"File.fnmatch('${globAndInt._1}', '${localFile.s3Key}')")
                .toJava(classOf[Boolean])
                .asInstanceOf[Boolean]
            } map (_._2)
        )
    }
  }

  def resolveLocalFiles(implicit site: Site, logger: Logger): Either[ErrorReport, Seq[LocalFile]] = Try {
    val files = recursiveListFiles(new File(site.rootDirectory)).filterNot(_.isDirectory)
    files map { file =>
      val s3Key = site.resolveS3Key(file)
      LocalFile(s3Key, file, encodingOnS3(s3Key))
    } filterNot { file =>
      val excludeFile = site.config.exclude_from_upload exists { _.fold(
        // For backward compatibility, use Ruby regex matching
        (exclusionRegex: String) => rubyRegexMatches(file.s3Key, exclusionRegex),
        (exclusionRegexes: Seq[String]) => exclusionRegexes exists (rubyRegexMatches(file.s3Key, _))
      ) }
      if (excludeFile) logger.debug(s"Excluded ${file.s3Key} from upload")
      excludeFile
    } filterNot { _.originalFile.getName == "s3_website.yml" } // For security reasons, the s3_website.yml should never be pushed
  } match {
    case Success(localFiles) =>
      Right(
        // Sort by key, because this will improve the performance when pushing existing sites.
        // The lazy-loading diff take advantage of this arrangement.
        localFiles sortBy (_.s3Key)
      )
    case Failure(error) =>
      Left(IOError(error))
  }

  def recursiveListFiles(f: File): Seq[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  }
}

case class Redirect(key: String, redirectTarget: String)

object Redirect extends UploadType {
  def resolveRedirects(implicit config: Config): Seq[Upload with UploadTypeResolved] = {
    val redirects = config.redirects.fold(Nil: Seq[Redirect]) {
      sourcesToTargets =>
        sourcesToTargets.foldLeft(Seq(): Seq[Redirect]) {
          (redirects, sourceToTarget) =>
            redirects :+ Redirect(sourceToTarget._1, sourceToTarget._2)
        }
    }
    redirects.map { redirect =>
      Upload.apply(redirect)
    }
  }
}

case class Upload(
  s3Key: String,
  essence: Either[Redirect, UploadBody]
) extends S3KeyProvider {

  def withUploadType(ut: UploadType) =
    new Upload(s3Key, essence) with UploadTypeResolved {
      def uploadType = ut
    }
}

object Upload {
  def apply(redirect: Redirect): Upload with UploadTypeResolved = new Upload(redirect.key, Left(redirect)) with UploadTypeResolved {
    def uploadType = Redirect
  }
}

/**
 * Represents a bunch of data that should be stored into an S3 objects body.
 */
case class UploadBody(
  md5: MD5,
  contentLength: Long,
  contentEncoding: Option[String],
  maxAge: Option[Int],
  contentType: String,
  openInputStream: () => InputStream // It's in the caller's responsibility to close this stream
)

case class S3File(s3Key: String, md5: MD5)

object S3File {
  def apply(summary: S3ObjectSummary): S3File = S3File(summary.getKey, summary.getETag)
}
