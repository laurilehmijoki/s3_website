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
  sourceFile: File,
  encodingOnS3: Option[Either[Gzip, Zopfli]]
) extends S3KeyProvider

object LocalFile {
  def toUpload(localFile: LocalFile)(implicit config: Config): Either[ErrorReport, Upload] = Try {
    def fis(file: File): InputStream = new FileInputStream(file)
    def using[T <: Closeable, R](cl: T)(f: (T) => R): R = try f(cl) finally cl.close()
    val sourceFile: File = localFile
      .encodingOnS3
      .fold(localFile.sourceFile)(algorithm => {
      val tempFile = File.createTempFile(localFile.sourceFile.getName, "gzip")
      tempFile.deleteOnExit()
      using(new GZIPOutputStream(new FileOutputStream(tempFile))) { stream =>
        IOUtils.copy(fis(localFile.sourceFile), stream)
      }
      tempFile
    })
    val md5 = using(fis(sourceFile)) { inputStream =>
      DigestUtils.md5Hex(inputStream)
    }
    val maxAge = config.max_age.flatMap { maxAgeIntOrGlob =>
      maxAgeIntOrGlob.fold(
        (seconds: Int) => Some(seconds),
        (globs2Ints: Map[String, Int]) =>
          globs2Ints.find { globAndInt =>
            (rubyRuntime evalScriptlet s"File.fnmatch('${globAndInt._1}', '${localFile.s3Key}')")
              .toJava(classOf[Boolean])
              .asInstanceOf[Boolean]
          } map (_._2)
      )
    }

    Upload(
      s3Key = localFile.s3Key,
      essence = Right(
        UploadBody(
          md5 = md5,
          contentEncoding = localFile.encodingOnS3.map(_ => "gzip"),
          contentLength = sourceFile.length(),
          maxAge = maxAge,
          contentType = tika.detect(localFile.sourceFile),
          openInputStream = () => new FileInputStream(sourceFile)
        )
      )
    )
  } match {
    case Success(upload) => Right(upload)
    case Failure(error) => Left(IOError(error))
  }

  lazy val tika = new Tika()

  def resolveLocalFiles(implicit site: Site): Either[ErrorReport, Seq[LocalFile]] = Try {
    val files = recursiveListFiles(new File(site.rootDirectory)).filterNot(_.isDirectory)
    files map { file =>
      val s3Key = site.resolveS3Key(file)
      LocalFile(s3Key, file, encodingOnS3(s3Key))
    } filterNot { file =>
      site.config.exclude_from_upload exists { _.fold(
        // For backward compatibility, use Ruby regex matching
        (exclusionRegex: String) => rubyRegexMatches(file.s3Key, exclusionRegex),
        (exclusionRegexes: Seq[String]) => exclusionRegexes exists (rubyRegexMatches(file.s3Key, _))
      ) }
    } filterNot { _.sourceFile.getName == "s3_website.yml" } // For security reasons, the s3_website.yml should never be pushed
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

case class OverrideExisting()
case class CreateNew()

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
