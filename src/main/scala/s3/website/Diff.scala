package s3.website

import s3.website.model.Files.listSiteFiles
import s3.website.model._
import s3.website.Ruby.rubyRegexMatches
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import java.io.File

object Diff {

  type FutureUploads = Future[Either[ErrorReport, Seq[Upload]]]

  def resolveDiff(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                 (implicit site: Site, logger: Logger, executor: ExecutionContextExecutor): FutureUploads =
    resolveDiffAgainstGetBucketResponse(s3FilesFuture)

  private def resolveDiffAgainstGetBucketResponse(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                                                 (implicit site: Site, logger: Logger, executor: ExecutionContextExecutor): FutureUploads =
    s3FilesFuture.map { errorOrS3Files =>
      errorOrS3Files.right.flatMap { s3Files =>
        Try {
          val s3KeyIndex = s3Files.map(_.s3Key).toSet
          val s3Md5Index = s3Files.map(_.md5).toSet
          val siteFiles = listSiteFiles
          val existsOnS3 = (f: File) => s3KeyIndex contains site.resolveS3Key(f)
          val isChangedOnS3 = (upload: Upload) => !(s3Md5Index contains upload.md5.get)
          val newUploads = siteFiles collect {
            case file if !existsOnS3(file) => Upload(file, NewFile)
          }
          val changedUploads = siteFiles collect {
            case file if existsOnS3(file) => Upload(file, FileUpdate)
          } filter isChangedOnS3
          newUploads ++ changedUploads
        } match {
          case Success(ok) => Right(ok)
          case Failure(err) => Left(ErrorReport(err))
        }
      }
    }

  def resolveDeletes(s3Files: Future[Either[ErrorReport, Seq[S3File]]], redirects: Seq[Redirect])
                    (implicit site: Site, logger: Logger, executor: ExecutionContextExecutor): Future[Either[ErrorReport, Seq[S3Key]]] =
    if (site.config.ignore_on_server.contains(Left(DELETE_NOTHING_MAGIC_WORD))) {
      logger.debug(s"Ignoring all files on the bucket, since the setting $DELETE_NOTHING_MAGIC_WORD is on.")
      Future(Right(Nil))
    } else {
      val localS3Keys = listSiteFiles.map(site resolveS3Key)

      s3Files map { s3Files: Either[ErrorReport, Seq[S3File]] =>
        for {
          remoteS3Keys <- s3Files.right.map(_ map (_.s3Key)).right
        } yield {
          val keysToRetain = (localS3Keys ++ (redirects map { _.s3Key })).toSet
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
    }

  val DELETE_NOTHING_MAGIC_WORD = "_DELETE_NOTHING_ON_THE_S3_BUCKET_"
}