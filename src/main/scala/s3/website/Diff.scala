package s3.website

import s3.website.model._
import s3.website.Ruby.rubyRegexMatches
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import java.io.File
import org.apache.commons.io.FileUtils._
import org.apache.commons.codec.digest.DigestUtils._
import scala.io.Source
import s3.website.Diff.LocalFileDatabase.resolveDiffAgainstLocalDb
import s3.website.Diff.UploadBatch

case class Diff(
  unchanged: Future[Either[ErrorReport, Seq[S3Key]]],
  uploads: Seq[UploadBatch],
  persistenceError: Future[Option[ErrorReport]]
)

object Diff {

  type UploadBatch = Future[Either[ErrorReport, Seq[LocalFileFromDisk]]]

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
          val isChangedOnS3 = (localFile: LocalFileFromDisk) => !(s3Md5Index contains localFile.md5.get)
          val newFiles = siteFiles collect {
            case file if !existsOnS3(file) => LocalFileFromDisk(file, NewFile)
          }
          val changedFiles = siteFiles collect {
            case file if existsOnS3(file) => LocalFileFromDisk(file, FileUpdate)
          } filter isChangedOnS3
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
          case Success(ok) => Right(ok)
          case Failure(err) => Left(ErrorReport(err))
        }
      }
    }
    def collectResult[B](pf: PartialFunction[Either[DbRecord, LocalFileFromDisk],B]) =
      diffAgainstS3.map { errorOrDiffSource =>
        errorOrDiffSource.right map (_ collect pf)
      }
    val unchanged = collectResult {
      case Left(dbRecord) => dbRecord.s3Key
    }
    val uploads: UploadBatch = collectResult {
      case Right(localFile) => localFile
    }
    Right(Diff(unchanged, uploads :: Nil, persistenceError = Future(None)))
  }

  def resolveDeletes(diff: Diff, s3Files: Future[Either[ErrorReport, Seq[S3File]]], redirects: Seq[Redirect])
                    (implicit config: Config, logger: Logger, executor: ExecutionContextExecutor): Future[Either[ErrorReport, Seq[S3Key]]] = {
    val localKeys = for {
      errorOrUnchanged <- diff.unchanged
      errorsOrChanges  <- Future.sequence(diff.uploads)
    } yield
      errorsOrChanges.foldLeft(errorOrUnchanged: Either[ErrorReport, Seq[S3Key]]) { (memo, errorOrChanges) =>
        for {
          mem <- memo.right
          keysToDelete <- errorOrChanges.right
        } yield mem ++ keysToDelete.map(_.s3Key)
      }

    s3Files zip localKeys map { (s3Files: Either[ErrorReport, Seq[S3File]], errorOrLocalKeys: Either[ErrorReport, Seq[S3Key]]) =>
      for {
        localS3Keys <- errorOrLocalKeys.right
        remoteS3Keys <- s3Files.right.map(_ map (_.s3Key)).right
      } yield {
        val keysToRetain = (localS3Keys ++ (redirects map { _.s3Key })).toSet
        remoteS3Keys filterNot { s3Key =>
          val ignoreOnServer = config.ignore_on_server.exists(_.fold(
            (ignoreRegex: String) => rubyRegexMatches(s3Key, ignoreRegex),
            (ignoreRegexes: Seq[String]) => ignoreRegexes.exists(rubyRegexMatches(s3Key, _))
          ))
          if (ignoreOnServer) logger.debug(s"Ignoring $s3Key on server")
          (keysToRetain contains s3Key) || ignoreOnServer
        }
      }
    }.tupled
  }

  object LocalFileDatabase {
    def hasRecords(implicit site: Site, logger: Logger) =
      (for {
        dbFile <- getOrCreateDbFile
        databaseIndices <- loadDbFromFile(dbFile)
      } yield databaseIndices.fullIndex.headOption.isDefined) getOrElse false

    def resolveDiffAgainstLocalDb(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                                 (implicit site: Site, logger: Logger, executor: ExecutionContextExecutor): Either[ErrorReport, Diff] = {
      val localDiff: Either[ErrorReport, Seq[Either[DbRecord, LocalFileFromDisk]]] =
        (for {
          dbFile <- getOrCreateDbFile
          databaseIndices <- loadDbFromFile(dbFile)
        } yield {
          val siteFiles = Files.listSiteFiles
          val recordsOrChangedFiles = siteFiles.foldLeft(Seq(): Seq[Either[DbRecord, LocalFileFromDisk]]) { (localFiles, file) =>
            val truncatedKey = TruncatedDbRecord(file)
            val fileIsUnchanged = databaseIndices.truncatedIndex contains truncatedKey
            if (fileIsUnchanged)
              localFiles :+ Left(databaseIndices.fullIndex find (_.truncated == truncatedKey) get)
            else {
              val uploadType =
                if (databaseIndices.s3KeyIndex contains truncatedKey.s3Key) FileUpdate
                else NewFile
              localFiles :+ Right(LocalFileFromDisk(file, uploadType))
            }
          }
          logger.debug(s"Discovered ${siteFiles.length} files on the local site, of which ${recordsOrChangedFiles count (_.isRight)} are new or changed")
          recordsOrChangedFiles
        }) match {
          case Success(ok) => Right(ok)
          case Failure(err) => Left(ErrorReport(err))
        }

      localDiff.right map { localDiffResult =>
        val unchangedAccordingToLocalDiff = localDiffResult collect {
          case Left(f) => f
        }

        val uploadsAccordingToLocalDiff = localDiffResult collect {
          case Right(f) => f
        }

        val changesMissedByLocalDiff: Future[Either[ErrorReport, Seq[LocalFileFromDisk]]] = s3FilesFuture.map { errorOrS3Files =>
          for (s3Files <- errorOrS3Files.right) yield {
            val remoteS3Keys = s3Files.map(_.s3Key).toSet
            val localS3Keys = unchangedAccordingToLocalDiff.map(_.s3Key).toSet
            val localMd5 = unchangedAccordingToLocalDiff.map(_.uploadFileMd5).toSet
            def isChangedOnS3(s3File: S3File) = (localS3Keys contains s3File.s3Key) && !(localMd5 contains s3File.md5)
            val changedOnS3 = s3Files collect {
              case s3File if isChangedOnS3(s3File) =>
                LocalFileFromDisk(site resolveFile s3File, FileUpdate)
            }
            val missingFromS3 = localS3Keys collect {
              case localS3Key if !(remoteS3Keys contains localS3Key) =>
                LocalFileFromDisk(site resolveFile localS3Key, NewFile)

            }
            changedOnS3 ++ missingFromS3
          }
        }

        val errorOrDiffAgainstS3 =
          changesMissedByLocalDiff map { errorOrUploads =>
            errorOrUploads.right map { uploadsMissedByLocalDiff =>
              val uploadsS3KeyIndex = uploadsMissedByLocalDiff.map(_.s3Key).toSet
              val unchangedAccordingToLocalAndS3Diff = unchangedAccordingToLocalDiff.filterNot(uploadsS3KeyIndex contains _.s3Key)
              (unchangedAccordingToLocalAndS3Diff, uploadsMissedByLocalDiff)
            }
          }

        val unchangedFilesFinal = errorOrDiffAgainstS3 map {
          _ fold (
            (error: ErrorReport) => Left(error),
            (syncResult: (Seq[DbRecord], Seq[LocalFileFromDisk])) => Right(syncResult._1)
          )
        }

        val changedAccordingToS3Diff = errorOrDiffAgainstS3.map {
          _ fold (
            (error: ErrorReport) => Left(error),
            (syncResult: (Seq[DbRecord], Seq[LocalFileFromDisk])) => Right(syncResult._2)
          )
        }
        val persistenceError: Future[Either[ErrorReport, _]] = for {
          unchanged <- unchangedFilesFinal
          changedAccordingToS3 <- changedAccordingToS3Diff
        } yield
          for {
            records1 <- unchanged.right
            records2 <- changedAccordingToS3.right
          } yield
            persist(records1.map(Left(_)) ++ records2.map(Right(_)) ++ uploadsAccordingToLocalDiff.map(Right(_))) match {
              case Success(_)   => Unit
              case Failure(err) => ErrorReport(err)
            }
        Diff(
          unchangedFilesFinal map (_.right.map(_ map (_.s3Key))),
          uploads = Future(Right(uploadsAccordingToLocalDiff)) :: changedAccordingToS3Diff :: Nil,
          persistenceError = persistenceError map (_.left.toOption)
        )
      }
    }

    private def getOrCreateDbFile(implicit site: Site, logger: Logger) =
      Try {
        val dbFile = new File(getTempDirectory, "s3_website_local_db_" + sha256Hex(site.rootDirectory))
        if (!dbFile.exists()) logger.debug("Creating a new database in " + dbFile.getName)
        dbFile.createNewFile()
        dbFile
      }
    
    case class DbIndices(
      s3KeyIndex:     Set[S3Key],
      truncatedIndex: Set[TruncatedDbRecord],
      fullIndex:      Set[DbRecord]
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
          fullIndex
        )
      }

    def persist(recordsOrChangedFiles: Seq[Either[DbRecord, LocalFileFromDisk]])(implicit site: Site, logger: Logger): Try[Seq[Either[DbRecord, LocalFileFromDisk]]] =
      getOrCreateDbFile flatMap { dbFile =>
        Try {
          val dbFileContents = recordsOrChangedFiles.map { recordOrChangedFile =>
            val record: DbRecord = recordOrChangedFile fold(
              record => record,
              changedFile => DbRecord(changedFile.s3Key, changedFile.originalFile.length, changedFile.originalFile.lastModified, changedFile.md5.get)
              )
            record.s3Key :: record.fileLength :: record.fileModified :: record.uploadFileMd5 :: Nil mkString "|"
          } mkString "\n"

          write(dbFile, dbFileContents)
          recordsOrChangedFiles
        }
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
  }
  
  object DbRecord {
    def apply(original: File)(implicit site: Site): DbRecord =
      DbRecord(site resolveS3Key original, original.length, original.lastModified, LocalFileFromDisk.md5(original).get)
  }
}