package s3.website

import s3.website.model._
import s3.website.Ruby.rubyRegexMatches
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import java.io.File
import org.apache.commons.io.FileUtils._
import org.apache.commons.codec.digest.DigestUtils._
import scala.io.Source
import s3.website.Diff.LocalFileDatabase.{DbRecord, resolveDiffAgainstLocalDb}
import s3.website.Diff.UploadBatch

case class Diff(
  uploads: Seq[UploadBatch],
  persistenceError: Future[Option[ErrorReport]]
)

object Diff {

  type UploadBatch = Future[Either[ErrorReport, Seq[Upload]]]

  def resolveDiff(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                 (implicit site: Site, logger: Logger, executor: ExecutionContextExecutor): Either[ErrorReport, Diff] =
    if (LocalFileDatabase.hasRecords) resolveDiffAgainstLocalDb(s3FilesFuture)
    else resolveDiffAgainstGetBucketResponse(s3FilesFuture)

  private def resolveDiffAgainstGetBucketResponse(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                                                 (implicit site: Site, logger: Logger, executor: ExecutionContextExecutor): Either[ErrorReport, Diff] = {
    val diffAgainstS3 = s3FilesFuture.map { errorOrS3Files =>
      errorOrS3Files.right.flatMap { s3Files =>
        Try {
          val s3KeyIndex = s3Files.map(_.s3Key).toSet
          val s3Md5Index = s3Files.map(_.md5).toSet
          val siteFiles = Files.listSiteFiles
          val existsOnS3 = (f: File) => s3KeyIndex contains site.resolveS3Key(f)
          val isChangedOnS3 = (upload: Upload) => !(s3Md5Index contains upload.md5.get)
          val newUploads = siteFiles collect {
            case file if !existsOnS3(file) => Upload(file, NewFile, reasonForUpload = MissingFromS3)
          }
          val changedUploads = siteFiles collect {
            case file if existsOnS3(file) => Upload(file, FileUpdate, reasonForUpload = Md5ChangedOnS3)
          } filter isChangedOnS3
          val unchangedFiles = {
            val newOrChangedFiles = (changedUploads ++ newUploads).map(_.originalFile).toSet
            siteFiles.filterNot(f => newOrChangedFiles contains f)
          }
          val recordsAndUploads: Seq[Either[DbRecord, Upload]] = unchangedFiles.map {
            f => Left(DbRecord(f))
          } ++ (changedUploads ++ newUploads).map {
            Right(_)
          }
          LocalFileDatabase persist recordsAndUploads
          recordsAndUploads
        } match {
          case Success(ok) => Right(ok)
          case Failure(err) => Left(ErrorReport(err))
        }
      }
    }
    def collectResult[B](pf: PartialFunction[Either[DbRecord, Upload], B]) =
      diffAgainstS3.map { errorOrDiffSource =>
        errorOrDiffSource.right map (_ collect pf)
      }
    val uploads: UploadBatch = collectResult {
      case Right(upload) => upload
    }
    Right(Diff(uploads :: Nil, persistenceError = Future(None)))
  }

  def resolveDeletes(diff: Diff, s3Files: Future[Either[ErrorReport, Seq[S3File]]], redirects: Seq[Redirect])
                    (implicit site: Site, logger: Logger, executor: ExecutionContextExecutor): Future[Either[ErrorReport, Seq[S3Key]]] =
    s3Files map { s3Files =>
      val localS3Keys = Files.listSiteFiles.map(site resolveS3Key)
      for {
        remoteS3Keys <- s3Files.right.map(_ map (_.s3Key)).right
      } yield {
        val keysToRetain = (localS3Keys ++ (redirects map {
          _.s3Key
        })).toSet
        remoteS3Keys filterNot { s3Key =>
          val ignoreOnServer = site.config.ignore_on_server.exists(_.fold(
            (ignoreRegex: String) => rubyRegexMatches(s3Key, ignoreRegex),
            (ignoreRegexes: Seq[String]) => ignoreRegexes.exists(rubyRegexMatches(s3Key, _))
          ))
          if (ignoreOnServer) logger.debug(s"Ignoring $s3Key on server")
          (keysToRetain contains s3Key) || ignoreOnServer
        }
      }
    }

  object LocalFileDatabase {
    def hasRecords(implicit site: Site, logger: Logger) =
      (for {
        dbFile <- getOrCreateDbFile
        databaseIndices <- loadDbFromFile(dbFile)
      } yield databaseIndices.fullIndex.headOption.isDefined) getOrElse false

    def resolveDiffAgainstLocalDb(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                                 (implicit site: Site, logger: Logger, executor: ExecutionContextExecutor): Either[ErrorReport, Diff] = {
      localDiff.right map { localDiffResult =>
        val uploadsAccordingToLocalDiff = localDiffResult collect {
          case Right(f) => f
        }

        val unchangedAccordingToLocalDiff = localDiffResult collect {
          case Left(f) => f
        }

        val uploadsAccordingToS3Diff: Future[Either[ErrorReport, Seq[Upload]]] = s3FilesFuture.map { errorOrS3Files =>
          for (s3Files <- errorOrS3Files.right) yield {
            val remoteS3Keys = s3Files.map(_.s3Key).toSet
            val localS3Keys = unchangedAccordingToLocalDiff.map(_.s3Key).toSet
            val localMd5 = unchangedAccordingToLocalDiff.map(_.uploadFileMd5).toSet
            def isChangedOnS3(s3File: S3File) = (localS3Keys contains s3File.s3Key) && !(localMd5 contains s3File.md5)
            val changedOnS3 = s3Files collect {
              case s3File if isChangedOnS3(s3File) =>
                Upload(site resolveFile s3File, FileUpdate, reasonForUpload = Md5ChangedOnS3)
            }
            val missingFromS3 = localS3Keys collect {
              case localS3Key if !(remoteS3Keys contains localS3Key) =>
                Upload(site resolveFile localS3Key, NewFile, reasonForUpload = MissingFromS3)

            }
            changedOnS3 ++ missingFromS3
          }
        }

        val persistenceError: Future[Either[ErrorReport, _]] = for {
          errorOrUploadsAccordingToS3 <- uploadsAccordingToS3Diff
        } yield
          for {
            uploadsAccordingToS3 <- errorOrUploadsAccordingToS3.right
          } yield {
            val uploads = uploadsAccordingToS3 ++ uploadsAccordingToLocalDiff
            persist(unchangedAccordingToLocalDiff.map(Left(_)) ++ uploads.map(Right(_))) match {
              case Success(_) => Unit
              case Failure(err) => ErrorReport(err)
            }
          }

        Diff(
          uploads = Future(Right(uploadsAccordingToLocalDiff)) :: uploadsAccordingToS3Diff :: Nil,
          persistenceError = persistenceError map (_.left.toOption)
        )
      }
    }

    private def localDiff(implicit site: Site, logger: Logger, executor: ExecutionContextExecutor):
    Either[ErrorReport, Seq[Either[DbRecord, Upload]]] =
      (for {
        dbFile <- getOrCreateDbFile
        databaseIndices <- loadDbFromFile(dbFile)
      } yield {
        val siteFiles = Files.listSiteFiles
        val recordsOrUploads = siteFiles.foldLeft(Seq(): Seq[Either[DbRecord, Upload]]) { (recordsOrUps, file) =>
          val truncatedKey = TruncatedDbRecord(file)
          val fileIsUnchanged = databaseIndices.truncatedIndex contains truncatedKey
          if (fileIsUnchanged)
            recordsOrUps :+ Left(databaseIndices.fullIndex find (_.truncated == truncatedKey) get)
          else {
            val isUpdate = databaseIndices.s3KeyIndex contains truncatedKey.s3Key

            val uploadType =
              if (isUpdate) FileUpdate
              else NewFile
            recordsOrUps :+ Right(Upload(file, uploadType, reasonForUpload(truncatedKey, databaseIndices, isUpdate)))
          }
        }
        logger.debug(s"Discovered ${siteFiles.length} files on the local site, of which ${recordsOrUploads count (_.isRight)} are new or changed")
        recordsOrUploads
      }) match {
        case Success(ok) => Right(ok)
        case Failure(err) => Left(ErrorReport(err))
      }

    private def reasonForUpload(truncatedKey: TruncatedDbRecord, databaseIndices: DbIndices, isUpdate: Boolean) = {
      if (isUpdate) {
        val lengthChanged = !(databaseIndices.fileLenghtIndex contains truncatedKey.fileLength)
        val mtimeChanged = !(databaseIndices.lastModifiedIndex contains truncatedKey.fileModified)
        if (mtimeChanged && lengthChanged) LocalLengthAndMtimeChanged
        else if (lengthChanged) LocalLengthChanged
        else if (mtimeChanged) LocalMtimeChanged
        else UnknownReason
      }
      else FileIsNew
    }

    private def getOrCreateDbFile(implicit site: Site, logger: Logger) =
      Try {
        val dbFile = new File(getTempDirectory, "s3_website_local_db_" + sha256Hex(site.rootDirectory))
        if (!dbFile.exists()) logger.debug("Creating a new database in " + dbFile.getName)
        dbFile.createNewFile()
        dbFile
      }

    case class DbIndices(
                          s3KeyIndex: Set[S3Key],
                          fileLenghtIndex: Set[Long],
                          lastModifiedIndex: Set[Long],
                          truncatedIndex: Set[TruncatedDbRecord],
                          fullIndex: Set[DbRecord]
                          )

    private def loadDbFromFile(databaseFile: File)(implicit site: Site, logger: Logger): Try[DbIndices] =
      Try {
        // record format: "s3Key(file.path)|length(file)|mtime(file)|md5Hex(file.encoded)"
        val RecordRegex = "(.*?)\\|(\\d+)\\|(\\d+)\\|([a-f0-9]{32})".r
        val fullIndex = Source
          .fromFile(databaseFile, "utf-8")
          .getLines()
          .toStream
          .map {
          case RecordRegex(s3Key, fileLength, fileModified, md5) =>
            DbRecord(s3Key, fileLength.toLong, fileModified.toLong, md5)
        }
          .toSet
        DbIndices(
          s3KeyIndex = fullIndex map (_.s3Key),
          truncatedIndex = fullIndex map (TruncatedDbRecord(_)),
          fileLenghtIndex = fullIndex map (_.fileLength),
          lastModifiedIndex = fullIndex map (_.fileModified),
          fullIndex = fullIndex
        )
      }

    def persist(recordsOrUploads: Seq[Either[DbRecord, Upload]])(implicit site: Site, logger: Logger): Try[Seq[Either[DbRecord, Upload]]] =
      getOrCreateDbFile flatMap { dbFile =>
        Try {
          val dbFileContents = recordsOrUploads.map { recordOrUpload =>
            val record: DbRecord = recordOrUpload fold(
              record => record,
              upload => DbRecord(upload)
              )
            record.asString
          } mkString "\n"

          write(dbFile, dbFileContents)
          recordsOrUploads
        }
      }

    case class TruncatedDbRecord(s3Key: String, fileLength: Long, fileModified: Long)

    object TruncatedDbRecord {
      def apply(dbRecord: DbRecord): TruncatedDbRecord = TruncatedDbRecord(dbRecord.s3Key, dbRecord.fileLength, dbRecord.fileModified)

      def apply(file: File)(implicit site: Site): TruncatedDbRecord = TruncatedDbRecord(site resolveS3Key file, file.length, file.lastModified)
    }

    /**
     * @param uploadFileMd5 if the file is gzipped, this checksum should be calculated on the gzipped file, not the original file
     */
    case class DbRecord(s3Key: String, fileLength: Long, fileModified: Long, uploadFileMd5: MD5) {
      lazy val truncated = TruncatedDbRecord(s3Key, fileLength, fileModified)

      lazy val asString = (s3Key :: fileLength :: fileModified :: uploadFileMd5 :: Nil).mkString("|")
    }

    object DbRecord {
      def apply(original: File)(implicit site: Site): DbRecord =
        DbRecord(site resolveS3Key original, original.length, original.lastModified, Upload.md5(original).get)

      def apply(upload: Upload): DbRecord =
        DbRecord(upload.s3Key, upload.originalFile.length, upload.originalFile.lastModified, upload.md5.get)
    }
  }
}