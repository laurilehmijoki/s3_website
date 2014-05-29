package s3.website.model

import com.amazonaws.services.s3.model.S3ObjectSummary
import java.io._
import org.apache.commons.codec.digest.DigestUtils
import java.util.zip.GZIPOutputStream
import org.apache.tika.Tika
import s3.website.Ruby._
import s3.website._
import s3.website.model.LocalFileFromDisk.tika
import s3.website.model.Encoding.encodingOnS3
import java.io.File.createTempFile
import org.apache.commons.io.IOUtils.copy
import scala.util.Try

object Encoding {

  val defaultGzipExtensions = ".html" :: ".css" :: ".js" :: ".txt" :: Nil

  case class Gzip()
  case class Zopfli()

  def encodingOnS3(s3Key: String)(implicit config: Config): Option[Either[Gzip, Zopfli]] =
    config.gzip.flatMap { (gzipSetting: Either[Boolean, Seq[String]]) =>
      val shouldZipThisFile = gzipSetting.fold(
        shouldGzip => defaultGzipExtensions exists s3Key.endsWith,
        fileExtensions => fileExtensions exists s3Key.endsWith
      )
      if (shouldZipThisFile && config.gzip_zopfli.isDefined)
        Some(Right(Zopfli()))
      else if (shouldZipThisFile)
        Some(Left(Gzip()))
      else
        None
    }
}

sealed trait UploadType // Sealed, so that we can avoid inexhaustive pattern matches more easily

case object NewFile extends UploadType
case object FileUpdate extends UploadType
case object RedirectFile extends UploadType

case class LocalFileFromDisk(originalFile: File, uploadType: UploadType)(implicit site: Site) {
  lazy val s3Key = site.resolveS3Key(originalFile)

  lazy val encodingOnS3 = Encoding.encodingOnS3(s3Key)

  /**
   * This is the file we should upload, because it contains the potentially gzipped contents of the original file.
   *
   * May throw an exception, so remember to call this in a Try or Future monad
   */
  lazy val uploadFile: Try[File] = LocalFileFromDisk uploadFile originalFile

  lazy val contentType: Try[String] = tika map { tika =>
    val mimeType = tika.detect(originalFile)
    if (mimeType.startsWith("text/") || mimeType == "application/json")
      mimeType + "; charset=utf-8"
    else
      mimeType
  }

  lazy val maxAge: Option[Int] = {
    type GlobsMap = Map[String, Int]
    site.config.max_age.flatMap { (intOrGlobs: Either[Int, GlobsMap]) =>
      type GlobsSeq = Seq[(String, Int)]
      def respectMostSpecific(globs: GlobsMap): GlobsSeq = globs.toSeq.sortBy(_._1.length).reverse
      intOrGlobs
        .right.map(respectMostSpecific)
        .fold(
          (seconds: Int) => Some(seconds),
          (globs: GlobsSeq) =>
            globs.find { globAndInt =>
              (rubyRuntime evalScriptlet s"File.fnmatch('${globAndInt._1}', '$s3Key')")
                .toJava(classOf[Boolean])
                .asInstanceOf[Boolean]
            } map (_._2)
        )
    }
  }

  /**
   * May throw an exception, so remember to call this in a Try or Future monad
   */
  lazy val md5 = LocalFileFromDisk md5 originalFile
}

object LocalFileFromDisk {
  lazy val tika = Try(new Tika())

  def md5(originalFile: File)(implicit site: Site): Try[MD5] =
    uploadFile(originalFile) map { file =>
      using(fis { file }) { DigestUtils.md5Hex }
    }

  def uploadFile(originalFile: File)(implicit site: Site): Try[File] =
    encodingOnS3(site resolveS3Key originalFile)
      .fold(Try(originalFile))(algorithm =>
        Try {
          val tempFile = createTempFile(originalFile.getName, "gzip")
          tempFile.deleteOnExit()
          using(new GZIPOutputStream(new FileOutputStream(tempFile))) { stream =>
            copy(fis(originalFile), stream)
          }
          tempFile
        }
      )

  private[this] def fis(file: File): InputStream = new FileInputStream(file)
  private[this] def using[T <: Closeable, R](cl: T)(f: (T) => R): R = try f(cl) finally cl.close()
}

object Files {
  def recursiveListFiles(f: File): Seq[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  }

  def listSiteFiles(implicit site: Site, logger: Logger) = {
    def excludeFromUpload(s3Key: String) = {
      val excludeByConfig = site.config.exclude_from_upload exists {
        _.fold(
          // For backward compatibility, use Ruby regex matching
          (exclusionRegex: String) => rubyRegexMatches(s3Key, exclusionRegex),
          (exclusionRegexes: Seq[String]) => exclusionRegexes exists (rubyRegexMatches(s3Key, _))
        )
      }
      val neverUpload = "s3_website.yml" :: ".env" :: Nil
      val doNotUpload = excludeByConfig || (neverUpload contains s3Key)
      if (doNotUpload) logger.debug(s"Excluded $s3Key from upload")
      doNotUpload
    }
    recursiveListFiles(new File(site.rootDirectory))
      .filterNot(_.isDirectory)
      .filterNot(f => excludeFromUpload(site.resolveS3Key(f)))
  }
}

case class Redirect(s3Key: String, redirectTarget: String) {
  def uploadType = RedirectFile
}

object Redirect {
  def resolveRedirects(implicit config: Config): Seq[Redirect] =
    config.redirects.fold(Nil: Seq[Redirect]) { sourcesToTargets =>
      sourcesToTargets.foldLeft(Seq(): Seq[Redirect]) {
        (redirects, sourceToTarget) =>
          redirects :+ Redirect(sourceToTarget._1, sourceToTarget._2)
      }
  }
}

case class S3File(s3Key: String, md5: MD5)

object S3File {
  def apply(summary: S3ObjectSummary): S3File = S3File(summary.getKey, summary.getETag)
}
