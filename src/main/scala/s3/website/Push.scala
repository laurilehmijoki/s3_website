package s3.website

import s3.website.model.Config.S3_website_yml
import s3.website.model.Redirect.{Redirects, resolveRedirects}
import s3.website.model.Site._
import scala.concurrent.{ExecutionContextExecutor, Future, Await}
import scala.concurrent.duration._
import scala.language.postfixOps
import s3.website.UploadHelper.{FutureUploads, resolveDeletes, resolveUploads}
import s3.website.S3._
import scala.concurrent.ExecutionContext.fromExecutor
import java.util.concurrent.Executors.newFixedThreadPool
import java.util.concurrent.ExecutorService
import s3.website.model._
import s3.website.model.FileUpdate
import s3.website.model.NewFile
import s3.website.S3.PushSuccessReport
import scala.collection.mutable.ArrayBuffer
import s3.website.CloudFront._
import s3.website.S3.SuccessfulDelete
import s3.website.CloudFront.SuccessfulInvalidation
import s3.website.S3.S3Setting
import s3.website.CloudFront.CloudFrontSetting
import s3.website.S3.SuccessfulUpload
import s3.website.CloudFront.FailedInvalidation
import java.io.File
import com.lexicalscope.jewel.cli.CliFactory.parseArguments
import s3.website.ByteHelper.humanReadableByteCount
import s3.website.S3.SuccessfulUpload.humanizeUploadSpeed

object Push {

  def main(args: Array[String]) {
    implicit val cliArgs = parseArguments(classOf[CliArgs], args:_*)
    implicit val logger: Logger = new Logger(cliArgs.verbose)
    implicit val s3Settings = S3Setting()
    implicit val cloudFrontSettings = CloudFrontSetting()
    implicit val workingDirectory = new File(System.getProperty("user.dir")).getAbsoluteFile

    System exit push
  }

  trait CliArgs {
    import com.lexicalscope.jewel.cli.Option

    @Option(defaultToNull = true) def site: String
    @Option(longName = Array("config-dir"), defaultToNull = true) def configDir: String
    @Option def verbose: Boolean
    @Option(longName = Array("dry-run")) def dryRun: Boolean
    @Option(longName = Array("force")) def force: Boolean
  }

  def push(implicit cliArgs: CliArgs, s3Settings: S3Setting, cloudFrontSettings: CloudFrontSetting, workingDirectory: File, logger: Logger): ExitCode = {
    implicit val pushOptions = new PushOptions {
      def dryRun = cliArgs.dryRun
      def force  = cliArgs.force
    }

    implicit val yamlConfig = S3_website_yml(new File(Option(cliArgs.configDir).getOrElse(workingDirectory.getPath) + "/s3_website.yml"))

    val errorOrPushStatus = for (
      loadedSite <- loadSite.right
    ) yield {
      implicit val site = loadedSite
      val threadPool = newFixedThreadPool(site.config.concurrency_level)
      implicit val executor = fromExecutor(threadPool)
      val pushStatus = pushSite
      threadPool.shutdownNow()
      pushStatus
    }

    errorOrPushStatus.left foreach (err => logger.fail(s"Could not load the site: ${err.reportMessage}"))
    errorOrPushStatus fold((err: ErrorReport) => 1, pushStatus => pushStatus)
  }

  def pushSite(
                implicit site: Site,
                executor: ExecutionContextExecutor,
                s3Settings: S3Setting,
                cloudFrontSettings: CloudFrontSetting,
                logger: Logger,
                pushOptions: PushOptions
                ): ExitCode = {
    logger.info(s"${Deploy.renderVerb} ${site.rootDirectory}/* to ${site.config.s3_bucket}")
    val s3FilesFuture = resolveS3Files()
    val redirectsFuture = resolveRedirects(s3FilesFuture)
    val redirectReports = redirectsFuture.map { errOrRedirects =>
      errOrRedirects.right.map(_.filter(_.needsUpload).map(S3 uploadRedirect _))
    }

    val uploadReports = for {
      errorOrUploads <- resolveUploads(s3FilesFuture)
    } yield errorOrUploads.right.map(_.map(S3 uploadFile _))

    val deleteReports = redirectsFuture flatMap { errOrRedirects =>
      errOrRedirects.fold(
        err => Future(Left(err)),
        redirects => resolveDeletes(s3FilesFuture, redirects)
      )
    } map { deletes =>
      deletes.right.map(keysToDelete => keysToDelete.map(S3 delete _))
    }

    val allReports = Future.sequence(redirectReports :: uploadReports :: deleteReports :: Nil) map { reports =>
      reports.foldLeft(Nil: PushReports) { (memo, report: Either[ErrorReport, Seq[Future[PushErrorOrSuccess]]]) =>
        report match {
          case Left(err) =>
            memo :+ Left(err)
          case Right(pushResults: Seq[Future[PushErrorOrSuccess]]) =>
            memo ++ pushResults.map(Right(_))
        }
      }
    }
    val finishedPushOps = awaitForResults(Await.result(allReports, 1 day))
    val invalidationSucceeded = invalidateCloudFrontItems(finishedPushOps)
    
    report(finishedPushOps, invalidationSucceeded)
  }
  
  def invalidateCloudFrontItems
    (finishedPushOperations: FinishedPushOperations)
    (implicit config: Config, cloudFrontSettings: CloudFrontSetting, ec: ExecutionContextExecutor, logger: Logger, pushOptions: PushOptions):
  Option[InvalidationSucceeded] =
    config.cloudfront_distribution_id.map { distributionId =>
      val pushSuccessReports = 
        finishedPushOperations.map {
          ops =>
            for {
              failedOrSucceededPushes <- ops.right
              successfulPush <- failedOrSucceededPushes.right
            } yield successfulPush
        }.foldLeft(Seq(): Seq[PushSuccessReport]) {
          (reports, failOrSucc) =>
            failOrSucc.fold(
              _ => reports,
              (pushSuccessReport: PushSuccessReport) => reports :+ pushSuccessReport
            )
        }
      val invalidationResults: Seq[Either[FailedInvalidation, SuccessfulInvalidation]] =
        toInvalidationBatches(pushSuccessReports) map { invalidationBatch =>
          Await.result(
            CloudFront.invalidate(invalidationBatch, distributionId),
            atMost = 1 day
          )
        }
      if (invalidationResults.exists(_.isLeft))
        false // If one of the invalidations failed, mark the whole process as failed
      else
        true
    }

  type InvalidationSucceeded = Boolean

  def report(finishedPushOps: FinishedPushOperations, invalidationSucceeded: Option[Boolean])
                       (implicit config: Config, logger: Logger, pushOptions: PushOptions): ExitCode = {
    val pushCounts = resolvePushCounts(finishedPushOps)
    logger.info(s"Summary: ${pushCountsToString(pushCounts)}")
    val pushOpExitCode = finishedPushOps.foldLeft(0) { (memo, finishedUpload) =>
      memo + finishedUpload.fold(
        (error: ErrorReport) => 1,
        (failedOrSucceededUpload: Either[PushFailureReport, PushSuccessReport]) =>
          if (failedOrSucceededUpload.isLeft) 1 else 0
      )
    } min 1
    val cloudFrontInvalidationExitCode = invalidationSucceeded.fold(0)(allInvalidationsSucceeded =>
      if (allInvalidationsSucceeded) 0 else 1
    )

    val exitCode = (pushOpExitCode + cloudFrontInvalidationExitCode) min 1

    exitCode match {
      case 0 if !pushOptions.dryRun && pushCounts.thereWasSomethingToPush =>
        logger.info(s"Successfully pushed the website to http://${config.s3_bucket}.${config.s3_endpoint.s3WebsiteHostname}")
      case 1 =>
        logger.fail(s"Failed to push the website to http://${config.s3_bucket}.${config.s3_endpoint.s3WebsiteHostname}")
      case _ =>
    }
    exitCode
  }

  def awaitForResults(uploadReports: PushReports)(implicit executor: ExecutionContextExecutor): FinishedPushOperations =
    uploadReports map (_.right.map {
      rep => Await.result(rep, 1 day)
    })

  def resolvePushCounts(implicit finishedOperations: FinishedPushOperations) = finishedOperations.foldLeft(PushCounts()) {
    (counts: PushCounts, uploadReport) =>
      uploadReport.fold(
        (error: ErrorReport) => counts.copy(failures = counts.failures + 1),
        failureOrSuccess => failureOrSuccess.fold(
          (failureReport: PushFailureReport) => counts.copy(failures = counts.failures + 1),
          (successReport: PushSuccessReport) =>
            successReport match {
              case succ: SuccessfulUpload => succ.details.fold(_.uploadType, _.uploadType) match {
                case NewFile      => counts.copy(newFiles = counts.newFiles + 1).addTransferStats(succ) // TODO nasty repetition here
                case FileUpdate   => counts.copy(updates = counts.updates + 1).addTransferStats(succ)
                case RedirectFile => counts.copy(redirects = counts.redirects + 1).addTransferStats(succ)
              }
            case SuccessfulDelete(_) => counts.copy(deletes = counts.deletes + 1)
          }
        )
      )
  }

  def pushCountsToString(pushCounts: PushCounts)(implicit pushOptions: PushOptions): String =
    pushCounts match {
      case PushCounts(updates, newFiles, failures, redirects, deletes, _, _)
        if updates == 0 && newFiles == 0 && failures == 0 && redirects == 0 && deletes == 0 =>
          PushNothing.renderVerb
      case PushCounts(updates, newFiles, failures, redirects, deletes, uploadedBytes, uploadDurations) =>
        val reportClauses: scala.collection.mutable.ArrayBuffer[String] = ArrayBuffer()
        if (updates > 0)       reportClauses += s"${Updated.renderVerb} ${updates ofType "file"}."
        if (newFiles > 0)      reportClauses += s"${Created.renderVerb} ${newFiles ofType "file"}."
        if (failures > 0)      reportClauses += s"${failures ofType "operation"} failed." // This includes both failed uploads and deletes.
        if (redirects > 0)     reportClauses += s"${Applied.renderVerb} ${redirects ofType "redirect"}."
        if (deletes > 0)       reportClauses += s"${Deleted.renderVerb} ${deletes ofType "file"}."
        if (uploadedBytes > 0) {
          val transferSuffix = humanizeUploadSpeed(uploadedBytes, uploadDurations: _*).fold(".")(speed => s", $speed.")
          reportClauses += s"${Transferred.renderVerb} ${humanReadableByteCount(uploadedBytes)}$transferSuffix"
        }
        reportClauses.mkString(" ")
    }

  case class PushCounts(
                         updates: Int = 0, 
                         newFiles: Int = 0, 
                         failures: Int = 0, 
                         redirects: Int = 0, 
                         deletes: Int = 0,
                         uploadedBytes: Long = 0,
                         uploadDurations: Seq[UploadDuration] = Nil
                         ) {
    val thereWasSomethingToPush = updates + newFiles + redirects + deletes > 0

    def addTransferStats(successfulUpload: SuccessfulUpload): PushCounts =
      copy(
        uploadedBytes = uploadedBytes + (successfulUpload.uploadSize getOrElse 0L),
        uploadDurations = uploadDurations ++ successfulUpload.details.fold(_.uploadDuration, _ => None)
      )
  }

  type FinishedPushOperations = Seq[Either[ErrorReport, PushErrorOrSuccess]]
  type PushReports = Seq[Either[ErrorReport, Future[PushErrorOrSuccess]]]
  case class PushResult(threadPool: ExecutorService, uploadReports: PushReports)
  type ExitCode = Int
}
