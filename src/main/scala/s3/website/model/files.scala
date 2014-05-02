package s3.website.model

import com.amazonaws.services.s3.model.S3ObjectSummary
import java.io._
import scala.util.Try
import s3.website.model.Encoding._
import org.apache.commons.codec.digest.DigestUtils
import java.util.zip.GZIPOutputStream
import s3.website.model.Encoding.Gzip
import scala.util.Failure
import scala.Some
import scala.util.Success
import s3.website.model.Encoding.Zopfli
import org.apache.commons.io.IOUtils
import org.apache.tika.Tika

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

case class LocalFile(
  path: String,
  sourceFile: File,
  encodingOnS3: Option[Either[Gzip, Zopfli]]
)

object LocalFile {
  def toUploadSource(localFile: LocalFile): Either[Error, UploadSource] = Try {
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
    UploadSource(
      s3Key = localFile.path,
      md5 = md5,
      contentEncoding = localFile.encodingOnS3.map(_ => "gzip"),
      contentLength = sourceFile.length(),
      contentType = tika.detect(localFile.sourceFile),
      openInputStream = () => new FileInputStream(sourceFile)
    )
  } match {
    case Success(uploadSource) => Right(uploadSource)
    case Failure(error) => Left(IOError(error))
  }

  lazy val tika = new Tika()

  def resolveLocalFiles(implicit site: Site): Either[Error, Seq[LocalFile]] = Try {
    val files = recursiveListFiles(new File(site.rootDirectory)).filterNot(_.isDirectory)
    files map { file =>
      val path = site.localFilePath(file)
      LocalFile(path, file, encodingOnS3(path))
    }
  } match {
    case Success(localFiles) =>
      Right(localFiles)
    case Failure(error) =>
      Left(IOError(error))
  }

  def recursiveListFiles(f: File): Seq[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  }
}

case class UploadSource(
  s3Key: String,
  md5: MD5,
  contentLength: Long,
  contentEncoding: Option[String],
  contentType: String,
  openInputStream: () => InputStream // It's in the caller's responsibility to close this stream
)

case class S3File(s3Key: String, md5: MD5)

object S3File {
  def apply(summary: S3ObjectSummary): S3File = S3File(summary.getKey, summary.getETag)
}
