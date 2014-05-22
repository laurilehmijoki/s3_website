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
import scala.util.Failure
import scala.util.Success
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.apache.commons.io.FileUtils.{write, getTempDirectory}
import scala.io.Source
import s3.website.model.LocalFileDatabase.ChangedFile
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import s3.website.model.LocalFileFromDisk.tika

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

case class LocalFileFromDisk(
  originalFile: File,
  uploadType: UploadType
)(implicit site: Site) {
  lazy val s3Key = site.resolveS3Key(originalFile)

  lazy val encodingOnS3 = Encoding.encodingOnS3(s3Key)

  lazy val lastModified = originalFile.lastModified

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

  lazy val contentType = {
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
  lazy val md5 = using(fis(uploadFile)) { inputStream =>
    DigestUtils.md5Hex(inputStream)
  }

  private[this] def fis(file: File): InputStream = new FileInputStream(file)
  private[this] def using[T <: Closeable, R](cl: T)(f: (T) => R): R = try f(cl) finally cl.close()
}

object LocalFileFromDisk {
  lazy val tika = new Tika()
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
      val doNotUpload = excludeByConfig || s3Key == "s3_website.yml"
      if (doNotUpload) logger.debug(s"Excluded $s3Key from upload")
      doNotUpload
    }
    recursiveListFiles(new File(site.rootDirectory))
      .filterNot(_.isDirectory)
      .filterNot(f => excludeFromUpload(site.resolveS3Key(f)))
  }
}

object LocalFileDatabase {
  type ChangedFile = LocalFileFromDisk
  
  def hasRecords(implicit site: Site, logger: Logger) =
    (for {
      dbFile <- getOrCreateDbFile
      database <- loadDbFromFile(dbFile)
    } yield database.headOption.isDefined) getOrElse false

  def resolveDiffWithLocalDb(implicit site: Site, logger: Logger): Either[ErrorReport, Seq[Either[DbRecord, ChangedFile]]] =
    (for {
      dbFile <- getOrCreateDbFile
      database <- loadDbFromFile(dbFile)
    } yield {
      val siteFiles = Files.listSiteFiles
      val recordsOrChangedFiles = siteFiles.foldLeft(Seq(): Seq[Either[DbRecord, ChangedFile]]) { (localFiles, file) =>
        val key = DbRecord(file)
        val fileIsUnchanged = database.exists(_ == key)
        if (fileIsUnchanged)
          localFiles :+ Left(key)
        else {
          val uploadType =
            if (database.exists(_.s3Key == key.s3Key))
              FileUpdate
            else
              NewFile
          localFiles :+ Right(LocalFileFromDisk(file, uploadType))
        }
      }
      logger.debug(s"Discovered ${siteFiles.length} files on the site, of which ${recordsOrChangedFiles count (_.isRight)} are new or changed")
      recordsOrChangedFiles
    }) flatMap { recordsOrChangedFiles =>
      persist(recordsOrChangedFiles)
    } match {
      case Success(changedFiles) => Right(changedFiles)
      case Failure(error) => Left(ErrorReport(error))
    }

  private def getOrCreateDbFile(implicit site: Site, logger: Logger) =
    Try {
      val dbFile = new File(getTempDirectory, "s3_website_local_db_" + sha256Hex(site.rootDirectory))
      if (!dbFile.exists()) logger.debug("Creating a new database in " + dbFile.getName)
      dbFile.createNewFile()
      dbFile
    }

  private def loadDbFromFile(databaseFile: File)(implicit site: Site, logger: Logger): Try[Stream[DbRecord]] =
    Try {
      // record format: "s3Key(file.path)|length(file)|mtime(file)"
      val RecordRegex = "(.*?)\\|(\\d+)\\|(\\d+)".r
      Source
        .fromFile(databaseFile, "utf-8")
        .getLines()
        .toStream
        .map {
          case RecordRegex(s3Key, fileLength, fileModified) =>
            DbRecord(s3Key, fileLength.toLong, fileModified.toLong)
        }
    }
  
  def persist(recordsOrChangedFiles: Seq[Either[DbRecord, ChangedFile]])(implicit site: Site, logger: Logger): Try[Seq[Either[DbRecord, ChangedFile]]] =
    getOrCreateDbFile flatMap { dbFile =>
      Try {
        val dbFileContents = recordsOrChangedFiles.map { recordOrChangedFile =>
          val record: DbRecord = recordOrChangedFile fold(
            record => record,
            changedFile => DbRecord(changedFile.s3Key, changedFile.originalFile.length, changedFile.originalFile.lastModified)
            )
          record.s3Key :: record.fileLength :: record.fileModified :: Nil mkString "|"
        } mkString "\n"

        write(dbFile, dbFileContents)
        recordsOrChangedFiles
      }
    }
}

case class DbRecord(s3Key: String, fileLength: Long, fileModified: Long)

object DbRecord {
  def apply(file: File)(implicit site: Site): DbRecord = DbRecord(site resolveS3Key file, file.length, file.lastModified)
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
