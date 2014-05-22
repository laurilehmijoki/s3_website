package s3.website

import s3.website.model._
import s3.website.Ruby.rubyRegexMatches
import scala.concurrent.{Await, Future}
import scala.util.Try
import java.io.File
import scala.concurrent.duration._
import org.apache.commons.io.FileUtils._
import org.apache.commons.codec.digest.DigestUtils._
import scala.io.Source
import s3.website.Diff.LocalFileDatabase.{resolveDiffAgainstLocalDb}
import scala.util.Failure
import scala.util.Success

case class Diff(
  unchanged: Seq[S3Key],
  uploads: Seq[LocalFileFromDisk]
)

object Diff {

  def resolveDiff(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                 (implicit site: Site, logger: Logger): Either[ErrorReport, Diff] = {
    val allFiles =
      if (LocalFileDatabase.hasRecords) resolveDiffAgainstLocalDb
      else resolveDiffAgainstGetBucketResponse(s3FilesFuture)
    allFiles.right map Diff.apply
  }

  private def apply(allFiles: Seq[Either[DbRecord, LocalFileFromDisk]]): Diff =
    Diff(
      unchanged = allFiles.seq.collect {
        case Left(dbRecord) => dbRecord.s3Key
      },
      uploads = allFiles.seq.collect {
        case Right(f) => f
      }
    )

  private def resolveDiffAgainstGetBucketResponse(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                                                 (implicit site: Site, logger: Logger): Either[ErrorReport, Seq[Either[DbRecord, LocalFileFromDisk]]] =
    Await.result(s3FilesFuture, 5 minutes).right flatMap { s3Files =>
      Try {
        val s3KeyIndex = s3Files.map(_.s3Key).toSet
        val s3Md5Index = s3Files.map(_.md5).toSet
        val siteFiles = Files.listSiteFiles
        val nameExistsOnS3 = (f: File) => s3KeyIndex contains site.resolveS3Key(f)
        val newFiles = siteFiles
          .filterNot(nameExistsOnS3)
          .map { f => LocalFileFromDisk(f, uploadType = NewFile)}
        val changedFiles =
          siteFiles
            .filter(nameExistsOnS3)
            .map(f => LocalFileFromDisk(f, uploadType = FileUpdate))
            .filterNot(localFile => s3Md5Index contains localFile.md5)
        val unchangedFiles = {
          val newOrChangedFiles = (changedFiles ++ newFiles).map(_.originalFile).toSet
          siteFiles.filterNot(f => newOrChangedFiles contains f)
        }
        val allFiles: Seq[Either[DbRecord, LocalFileFromDisk]] = unchangedFiles.map {
          f => Left(DbRecord(f))
        } ++ (changedFiles ++ newFiles).map {
          Right(_)
        }
        LocalFileDatabase persist allFiles
        allFiles
      } match {
        case Success(allFiles) => Right(allFiles)
        case Failure(err) => Left(ErrorReport(err))
      }
    }

  def resolveDeletes(diff: Diff, s3Files: Seq[S3File], redirects: Seq[Redirect])
                    (implicit config: Config, logger: Logger): Seq[S3File] = {
    val localS3Keys = diff.unchanged ++ diff.uploads.map(_.s3Key)
    val keysNotToBeDeleted: Set[String] = (localS3Keys ++ redirects.map(_.s3Key)).toSet
    s3Files.filterNot { s3File =>
      val ignoreOnServer = config.ignore_on_server.exists(_.fold(
        (ignoreRegex: String)        => rubyRegexMatches(s3File.s3Key, ignoreRegex),
        (ignoreRegexes: Seq[String]) => ignoreRegexes.exists(rubyRegexMatches(s3File.s3Key, _))
      ))
      if (ignoreOnServer) logger.debug(s"Ignoring ${s3File.s3Key} on server")
      keysNotToBeDeleted.exists(_ == s3File.s3Key) || ignoreOnServer
    }
  }

  object LocalFileDatabase {
    def hasRecords(implicit site: Site, logger: Logger) =
      (for {
        dbFile <- getOrCreateDbFile
        database <- loadDbFromFile(dbFile)
      } yield database.headOption.isDefined) getOrElse false

    def resolveDiffAgainstLocalDb(implicit site: Site, logger: Logger): Either[ErrorReport, Seq[Either[DbRecord, LocalFileFromDisk]]] =
      (for {
        dbFile <- getOrCreateDbFile
        database <- loadDbFromFile(dbFile)
      } yield {
        val siteFiles = Files.listSiteFiles
        val recordsOrChangedFiles = siteFiles.foldLeft(Seq(): Seq[Either[DbRecord, LocalFileFromDisk]]) { (localFiles, file) =>
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

    def persist(recordsOrChangedFiles: Seq[Either[DbRecord, LocalFileFromDisk]])(implicit site: Site, logger: Logger): Try[Seq[Either[DbRecord, LocalFileFromDisk]]] =
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
}

case class DbRecord(s3Key: String, fileLength: Long, fileModified: Long)

object DbRecord {
  def apply(file: File)(implicit site: Site): DbRecord = DbRecord(site resolveS3Key file, file.length, file.lastModified)
}
