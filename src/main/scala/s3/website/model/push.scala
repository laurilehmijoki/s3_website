package s3.website.model

import com.amazonaws.services.s3.model.S3ObjectSummary
import java.io._
import org.apache.commons.codec.digest.DigestUtils
import java.util.zip.GZIPOutputStream
import org.apache.tika.Tika
import s3.website.Ruby._
import s3.website._
import s3.website.model.Upload.tika
import s3.website.model.Encoding.encodingOnS3
import java.io.File.createTempFile
import org.apache.commons.io.IOUtils.copy
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

object Encoding {

  val defaultGzipExtensions = ".html" :: ".css" :: ".js" :: ".txt" :: ".ico" :: Nil

  case class Gzip()
  case class Zopfli()

  def encodingOnS3(s3Key: S3Key)(implicit config: Config): Option[Either[Gzip, Zopfli]] =
    config.gzip.flatMap { (gzipSetting: Either[Boolean, Seq[String]]) =>
      val shouldZipThisFile = gzipSetting.fold(
        shouldGzip => defaultGzipExtensions exists s3Key.key.endsWith,
        fileExtensions => fileExtensions exists s3Key.key.endsWith
      )
      if (shouldZipThisFile && config.gzip_zopfli.isDefined)
        Some(Right(Zopfli()))
      else if (shouldZipThisFile)
        Some(Left(Gzip()))
      else
        None
    }
}

sealed trait UploadType {
  val pushAction: PushAction
}

case object NewFile extends UploadType {
  val pushAction = Created
}
case object FileUpdate extends UploadType {
  val pushAction = Updated
}

case object RedirectFile extends UploadType {
  val pushAction = Redirected
}

case class Upload(originalFile: File, uploadType: UploadType)(implicit site: Site) {
  lazy val s3Key = site.resolveS3Key(originalFile)

  lazy val encodingOnS3 = Encoding.encodingOnS3(s3Key)

  /**
   * This is the file we should upload, because it contains the potentially gzipped contents of the original file.
   *
   * May throw an exception, so remember to call this in a Try or Future monad
   */
  lazy val uploadFile: Try[File] = Upload uploadFile originalFile

  lazy val contentType: Try[String] = tika map { tika =>
    val mimeType = tika.detect(originalFile)
    if (mimeType.startsWith("text/") || mimeType == "application/json")
      mimeType + "; charset=utf-8"
    else
      mimeType
  }

  lazy val maxAge: Option[Int] =
    site.config.max_age.flatMap(
      _ fold(
        (maxAge: Int) => Some(maxAge),
        (globs: S3KeyGlob[Int]) => globs.globMatch(s3Key)
      )
    )

  lazy val cacheControl: Option[String] =
    site.config.cache_control.flatMap(
      _ fold(
        (cacheCtrl: String) => Some(cacheCtrl),
        (globs: S3KeyGlob[String]) => globs.globMatch(s3Key)
      )
    )

  /**
   * May throw an exception, so remember to call this in a Try or Future monad
   */
  lazy val md5 = Upload md5 originalFile
}

object Upload {
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
    if (these != null)
      these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
    else
      Nil
  }

  def listSiteFiles(implicit site: Site, logger: Logger) = {
    def excludeFromUpload(s3Key: S3Key) = {
      val excludeByConfig = site.config.exclude_from_upload exists {
        _.s3KeyRegexes.exists(_ matches s3Key)
      }
      val neverUpload = "s3_website.yml" :: ".env" :: Nil map (k => S3Key.build(k, site.config.s3_key_prefix))
      val doNotUpload = excludeByConfig || (neverUpload contains s3Key)
      if (doNotUpload) logger.debug(s"Excluded $s3Key from upload")
      doNotUpload
    }
    recursiveListFiles(site.rootDirectory)
      .filterNot(_.isDirectory)
      .filterNot(f => excludeFromUpload(site.resolveS3Key(f)))
  }
}

case class Redirect(s3Key: S3Key, redirectTarget: String, needsUpload: Boolean) {
  def uploadType = RedirectFile
}

private case class RedirectSetting(source: S3Key, target: String)

object Redirect {
  type Redirects = Future[Either[ErrorReport, Seq[Redirect]]]

  def resolveRedirects(s3FileFutures: Future[Either[ErrorReport, Seq[S3File]]])
                      (implicit config: Config, executor: ExecutionContextExecutor, pushOptions: PushOptions): Redirects = {
    val redirectSettings = config.redirects.fold(Nil: Seq[RedirectSetting]) { sourcesToTargets =>
      sourcesToTargets.foldLeft(Seq(): Seq[RedirectSetting]) {
        (redirects, sourceToTarget) =>
          redirects :+ RedirectSetting(sourceToTarget._1, applyRedirectRules(sourceToTarget._2))
      }
    }
    def redirectsWithExistsOnS3Info =
      s3FileFutures.map(_.right.map { s3Files =>
        val existingRedirectKeys = s3Files.filter(_.size == 0).map(_.s3Key).toSet
        redirectSettings.map(redirectSetting => 
          Redirect(redirectSetting, needsUpload = !existingRedirectKeys.contains(redirectSetting.source))
        )
      })
    val uploadOnlyMissingRedirects =
      config.treat_zero_length_objects_as_redirects.contains(true) && !pushOptions.force
    val allConfiguredRedirects = Future(Right(redirectSettings.map(redirectSetting =>
      Redirect(redirectSetting, needsUpload = true)
    )))
    if (uploadOnlyMissingRedirects) 
      redirectsWithExistsOnS3Info 
    else 
      allConfiguredRedirects
  }

  private def applyRedirectRules(redirectTarget: String)(implicit config: Config) = {
    val isExternalRedirect = redirectTarget.matches("https?:\\/\\/.*")
    val isInSiteRedirect = redirectTarget.startsWith("/")
    if (isInSiteRedirect || isExternalRedirect)
      redirectTarget
    else
      s"${config.s3_key_prefix.map(prefix => s"/$prefix").getOrElse("")}/$redirectTarget"
  }

  def apply(redirectSetting: RedirectSetting, needsUpload: Boolean): Redirect =
      Redirect(redirectSetting.source, redirectSetting.target, needsUpload)
}

case class S3File(s3Key: S3Key, md5: MD5, size: Long)

object S3File {
  def apply(summary: S3ObjectSummary)(implicit site: Site): S3File =
    S3File(S3Key.build(summary.getKey, None), summary.getETag, summary.getSize)
}
