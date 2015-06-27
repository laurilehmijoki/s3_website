package s3.website

import s3.website.S3Key.isIgnoredBecauseOfPrefix
import s3.website.model.Files.listSiteFiles
import s3.website.model._
import s3.website.Ruby.rubyRegexMatches
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import java.io.File

object UploadHelper {

  type FutureUploads = Future[Either[ErrorReport, Seq[Upload]]]

  def resolveUploads(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                 (implicit site: Site, pushOptions: PushOptions, logger: Logger, executor: ExecutionContextExecutor): FutureUploads =
    resolveUploadsAgainstGetBucketResponse(s3FilesFuture)

  private def resolveUploadsAgainstGetBucketResponse(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                                                    (implicit site: Site,
                                                     pushOptions: PushOptions,
                                                     logger: Logger,
                                                     executor: ExecutionContextExecutor): FutureUploads =
    s3FilesFuture.map { errorOrS3Files =>
      errorOrS3Files.right.flatMap { s3Files =>
        Try {
          val s3KeyIndex = s3Files.map(_.s3Key).toSet
          val s3Md5Index = s3Files.map(s3File => (s3File.s3Key, s3File.md5)).toSet
          val siteFiles = listSiteFiles
          val existsOnS3 = (f: File) => s3KeyIndex contains site.resolveS3Key(f)
          val isChangedOnS3 = (upload: Upload) => !(s3Md5Index contains (upload.s3Key, upload.md5.get))
          val newUploads = siteFiles collect {
            case file if !existsOnS3(file) => Upload(file, NewFile)
          }
          val changedUploads = siteFiles collect {
            case file if existsOnS3(file) => Upload(file, FileUpdate)
          } filter (if (pushOptions.force) selectAllFiles else isChangedOnS3)
          newUploads ++ changedUploads
        } match {
          case Success(ok) => Right(ok)
          case Failure(err) => Left(ErrorReport(err))
        }
      }
    }

  val selectAllFiles = (upload: Upload) => true

  def resolveDeletes(s3Files: Future[Either[ErrorReport, Seq[S3File]]], redirects: Seq[Redirect])
                    (implicit site: Site, logger: Logger, executor: ExecutionContextExecutor): Future[Either[ErrorReport, Seq[S3Key]]] =
    if (site.config.ignore_on_server exists (
      ignoreRegexes => ignoreRegexes.s3KeyRegexes exists( regex => regex matches S3Key.build(DELETE_NOTHING_MAGIC_WORD, site.config.s3_key_prefix))
    )) {
      logger.debug(s"Ignoring all files on the bucket, since the setting $DELETE_NOTHING_MAGIC_WORD is on.")
      Future(Right(Nil))
    } else {
      val localS3Keys = listSiteFiles.map(site resolveS3Key)

      s3Files map { s3Files: Either[ErrorReport, Seq[S3File]] =>
        for {
          remoteS3Keys <- s3Files.right.map(_ map (_.s3Key)).right
        } yield {
          val keysIgnoredBecauseOf_s3_key_prefix = remoteS3Keys.filterNot(isIgnoredBecauseOfPrefix)
          val keysToRetain = (
            localS3Keys ++ (redirects map { _.s3Key }) ++ keysIgnoredBecauseOf_s3_key_prefix
          ).toSet
          remoteS3Keys filterNot { s3Key =>
            val ignoreOnServer = site.config.ignore_on_server.exists(_ matches s3Key)
            if (ignoreOnServer) logger.debug(s"Ignoring $s3Key on server")
            (keysToRetain contains s3Key) || ignoreOnServer
          }
        }
      }
    }

  val DELETE_NOTHING_MAGIC_WORD = "_DELETE_NOTHING_ON_THE_S3_BUCKET_"
}