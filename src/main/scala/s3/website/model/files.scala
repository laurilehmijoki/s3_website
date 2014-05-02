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

trait UploadType {
  def uploadType: Either[NewFile, Update]
}

case class NewFile()
case class Update()

case class LocalFile(
  s3Key: String,
  sourceFile: File,
  encodingOnS3: Option[Either[Gzip, Zopfli]]
)

object LocalFile {
  def toUpload(localFile: LocalFile)(implicit config: Config): Either[Error, Upload] = Try {
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
      md5 = md5,
      contentEncoding = localFile.encodingOnS3.map(_ => "gzip"),
      contentLength = sourceFile.length(),
      maxAge = maxAge,
      contentType = tika.detect(localFile.sourceFile),
      openInputStream = () => new FileInputStream(sourceFile)
    )
  } match {
    case Success(upload) => Right(upload)
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

case class OverrideExisting()
case class CreateNew()

case class Upload(
  s3Key: String,
  md5: MD5,
  contentLength: Long,
  contentEncoding: Option[String],
  maxAge: Option[Int],
  contentType: String,
  openInputStream: () => InputStream // It's in the caller's responsibility to close this stream
) {
  def withUploadType(ut: Either[NewFile, Update]) = {
    new Upload(s3Key, md5, contentLength, contentEncoding, maxAge, contentType, openInputStream) with UploadType {
      def uploadType = ut
    }
  }
}

case class S3File(s3Key: String, md5: MD5)

object S3File {
  def apply(summary: S3ObjectSummary): S3File = S3File(summary.getKey, summary.getETag)
}
