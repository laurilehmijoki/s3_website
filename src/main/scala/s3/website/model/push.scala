package s3.website.model

import com.amazonaws.services.s3.model.S3ObjectSummary
import java.io._
import scala.util.Try
import s3.website.model.Encoding._
import org.apache.commons.codec.digest.DigestUtils
import java.util.zip.GZIPOutputStream
import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.tika.Tika
import s3.website.Ruby._
import s3.website._
import s3.website.model.Encoding.Gzip
import scala.util.Failure
import scala.util.Success
import s3.website.model.Encoding.Zopfli
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.apache.commons.io.FileUtils.{write, getTempDirectory}
import scala.io.Source
import s3.website.model.LocalFile.recursiveListFiles

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

trait LocalFile extends S3KeyProvider {
  val s3Key: String
  val length: Long
  val encodingOnS3: Option[Either[Gzip, Zopfli]]
  def contentType: String
  def uploadFile: File
  def md5: MD5
}

case class LocalFileFromDisk(
  s3Key: String,
  originalFile: File,
  encodingOnS3: Option[Either[Gzip, Zopfli]]
) extends LocalFile {

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

  lazy val contentType = LocalFile.resolveContentType(originalFile)

  /**
   * May throw an exception, so remember to call this in a Try or Future monad
   */
  lazy val md5 = using(fis(uploadFile)) { inputStream =>
    DigestUtils.md5Hex(inputStream)
  }

  private[this] def fis(file: File): InputStream = new FileInputStream(file)
  private[this] def using[T <: Closeable, R](cl: T)(f: (T) => R): R = try f(cl) finally cl.close()
}

object LocalFileFromDisk {
  def apply(dbKeyAndFile: (LocalDbKey, File))(implicit config: Config): LocalFileFromDisk =
    LocalFileFromDisk(dbKeyAndFile._1.s3Key, dbKeyAndFile._2, encodingOnS3(dbKeyAndFile._1.s3Key))
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
          contentType = localFile.contentType,
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

  def resolveLocalFiles(implicit site: Site, logger: Logger): Either[ErrorReport, Seq[LocalFile]] =
    new LocalFileDatabase().resolveLocalFiles.right map { localFiles =>
      localFiles filterNot { file =>
        val excludeFile = site.config.exclude_from_upload exists { _.fold(
          // For backward compatibility, use Ruby regex matching
          (exclusionRegex: String) => rubyRegexMatches(file.s3Key, exclusionRegex),
          (exclusionRegexes: Seq[String]) => exclusionRegexes exists (rubyRegexMatches(file.s3Key, _))
        ) }
        if (excludeFile) logger.debug(s"Excluded ${file.s3Key} from upload")
        excludeFile
      } filterNot { _.s3Key == "s3_website.yml" } // For security reasons, the s3_website.yml should never be pushed
    }

  def recursiveListFiles(f: File): Seq[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  }
}

// Not thread safe
class LocalFileDatabase(implicit site: Site) {

  lazy val databaseFile = {
    val dbFile = new File(getTempDirectory, "s3_website_local_db_" + sha256Hex(site.rootDirectory))
    dbFile.createNewFile()
    dbFile
  }

  case class LocalFileFromDb(
                              s3Key: String,
                              uploadFile: File,
                              md5: MD5,
                              encodingOnS3: Option[Either[Gzip, Zopfli]],
                              length: Long,
                              contentType: String) extends LocalFile

  val database: Either[ErrorReport, MutableFileCache] = Try {
    // record format: "s3Key(file.path)|lenght(file)|mtime(file)|md5(file)|contentType(file)"
    val RecordRegex = "(.*?)\\|(\\d+)\\|(\\d+)\\|(.*?)\\|(.*)".r
    Source
      .fromFile(databaseFile, "utf-8")
      .getLines()
      .map {
        case RecordRegex(s3Key, fileLength, modified, md5, contentType) =>
          val length: Long = fileLength.toLong
          val mtime: Long = modified.toLong
          LocalDbKey(s3Key, length, mtime) -> LocalFileFromDb(s3Key, new File(site.rootDirectory + s"/$s3Key"), md5, encodingOnS3(s3Key), length, contentType)
      }.toMap

  } match {
    case Success(v) => Right(scala.collection.mutable.Map(v.toSeq: _*))
    case Failure(f) => Left(ErrorReport(f))
  }
  
  type MutableFileCache = scala.collection.mutable.Map[LocalDbKey, LocalFile] 

  def addToDb(newOrChangedRecords: Map[LocalDbKey, LocalFileFromDisk]): Either[ErrorReport, Unit] = database.right.flatMap { db =>
    db ++= newOrChangedRecords
    val dbFileContents = db.map { record =>
      val localFile: LocalFile = record._2
      (localFile.s3Key :: localFile.uploadFile.length :: localFile.uploadFile.lastModified :: localFile.md5 :: localFile.contentType :: Nil).mkString("|")
    }.mkString("\n")
    write(databaseFile, dbFileContents)
    Right(())
  }

  def resolveLocalFiles: Either[ErrorReport, Seq[LocalFile]] =
    database.right flatMap { db =>
      Try {
        val files = recursiveListFiles(new File(site.rootDirectory)).filterNot(_.isDirectory) // TODO go reactive
        val newOrChangedFiles =
          files.foldLeft(Map(): Map[LocalDbKey, LocalFileFromDisk]) { (map, file) =>
            val key = LocalDbKey(file)
            map + (key -> LocalFileFromDisk(key -> file))
          } filterNot (entry => db.contains(entry._1))
        addToDb(newOrChangedFiles)
        db.values.toSeq
      } match {
        case Success(localFiles) => Right(localFiles)
        case Failure(error) => Left(ErrorReport(error))
      }
    }
}

case class LocalDbKey(s3Key: String, fileLength: Long, modified: Long)

object LocalDbKey {
  def apply(file: File)(implicit site: Site): LocalDbKey = LocalDbKey(site resolveS3Key file, file.length, file.lastModified)
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
