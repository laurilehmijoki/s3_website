package s3.website

import s3.website.model.Site._
import scala.concurrent.{ExecutionContextExecutor, Future, Await}
import scala.concurrent.duration._
import com.lexicalscope.jewel.cli.CliFactory
import scala.language.postfixOps
import s3.website.Diff.{resolveUploads, resolveDeletes}
import s3.website.S3._
import scala.concurrent.ExecutionContext.fromExecutor
import java.util.concurrent.Executors.newFixedThreadPool
import s3.website.model.LocalFile.resolveLocalFiles
import scala.collection.parallel.ParSeq
import java.util.concurrent.ExecutorService
import s3.website.model._
import s3.website.Implicits._
import s3.website.model.Update
import s3.website.model.NewFile
import s3.website.S3.PushSuccessReport
import scala.collection.mutable.ArrayBuffer
import s3.website.CloudFront._
import s3.website.Logger._
import s3.website.S3.SuccessfulDelete
import s3.website.CloudFront.SuccessfulInvalidation
import s3.website.S3.S3Settings
import s3.website.CloudFront.CloudFrontSettings
import s3.website.S3.SuccessfulUpload
import s3.website.CloudFront.FailedInvalidation

object Push {

  def pushSite(
                implicit site: Site,
                executor: ExecutionContextExecutor,
                s3Settings: S3Settings,
                cloudFrontSettings: CloudFrontSettings
                ): ExitCode = {
    info(s"Deploying ${site.rootDirectory}/* to ${site.config.s3_bucket}")
    val utils: Utils = new Utils

    val redirects = Redirect.resolveRedirects
    val redirectResults = redirects.map(new S3() upload)

    val errorsOrReports = for {
      s3Files    <- Await.result(resolveS3Files(), 1 minutes).right
      localFiles <- resolveLocalFiles.right
    } yield {
      val deleteReports: PushReports = utils toParSeq resolveDeletes(localFiles, s3Files, redirects)
        .map { s3File => new S3() delete s3File.s3Key }
        .map { Right(_) } // To make delete reports type-compatible with upload reports
      val uploadReports: PushReports = utils toParSeq resolveUploads(localFiles, s3Files)
        .map { _.right.map(new S3() upload) }
      uploadReports ++ deleteReports ++ redirectResults.map(Right(_))
    }
    val errorsOrFinishedPushOps: Either[ErrorReport, FinishedPushOperations] = errorsOrReports.right map {
      uploadReports => awaitForUploads(uploadReports)
    }
    val invalidationSucceeded = invalidateCloudFrontItems(errorsOrFinishedPushOps)
    
    afterPushFinished(errorsOrFinishedPushOps, invalidationSucceeded)
  }
  
  def invalidateCloudFrontItems
    (errorsOrFinishedPushOps: Either[ErrorReport, FinishedPushOperations])
    (implicit config: Config, cloudFrontSettings: CloudFrontSettings, ec: ExecutionContextExecutor): Option[InvalidationSucceeded] = {
    config.cloudfront_distribution_id.map {
      distributionId =>
        val pushSuccessReports = errorsOrFinishedPushOps.fold(
          errors => Nil,
          finishedPushOps => {
            finishedPushOps.map {
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
          }
        )
        val invalidationResults: Seq[Either[FailedInvalidation, SuccessfulInvalidation]] =
          toInvalidationBatches(pushSuccessReports) map { invalidationBatch =>
            Await.result(
              new CloudFront().invalidate(invalidationBatch, distributionId),
              atMost = 1 day
            )
          }
        if (invalidationResults.exists(_.isLeft))
          false // If one of the invalidations failed, mark the whole process as failed
        else
          true
    }
  }

  type InvalidationSucceeded = Boolean

  def afterPushFinished(errorsOrFinishedUploads: Either[ErrorReport, FinishedPushOperations], invalidationSucceeded: Option[Boolean])(implicit config: Config): ExitCode = {
    errorsOrFinishedUploads.right.foreach { finishedUploads =>
      val pushCounts = pushCountsToString(resolvePushCounts(finishedUploads))
      info(s"Summary: $pushCounts")
    }
    errorsOrFinishedUploads.left foreach (err => fail(s"Encountered an error: ${err.reportMessage}"))
    val exitCode = errorsOrFinishedUploads.fold(
      _ => 1,
      finishedUploads => finishedUploads.foldLeft(0) { (memo, finishedUpload) =>
        memo + finishedUpload.fold(
          (error: ErrorReport) => 1,
          (failedOrSucceededUpload: Either[PushFailureReport, PushSuccessReport]) =>
            if (failedOrSucceededUpload.isLeft) 1 else 0
        )
      } min 1
    ) max invalidationSucceeded.fold(0)(allInvalidationsSucceeded =>
      if (allInvalidationsSucceeded) 0 else 1
    )

    if (exitCode == 0)
      info(s"Successfully pushed the website to http://${config.s3_bucket}.${config.s3_endpoint.s3WebsiteHostname}")
    else
      fail(s"Failed to push the website to http://${config.s3_bucket}.${config.s3_endpoint.s3WebsiteHostname}")
    exitCode
  }

  def awaitForUploads(uploadReports: PushReports)(implicit executor: ExecutionContextExecutor): FinishedPushOperations =
    uploadReports map (_.right.map {
      rep => Await.result(rep, 1 day)
    })

  def resolvePushCounts(implicit finishedOperations: FinishedPushOperations) = finishedOperations.foldLeft(PushCounts()) {
    (counts: PushCounts, uploadReport) => uploadReport.fold(
      (error: ErrorReport) => counts.copy(failures = counts.failures + 1),
      failureOrSuccess => failureOrSuccess.fold(
        (failureReport: PushFailureReport) => counts.copy(failures = counts.failures + 1),
        (successReport: PushSuccessReport) => successReport match {
          case SuccessfulUpload(upload) => upload.uploadType match {
            case NewFile  => counts.copy(newFiles = counts.newFiles + 1)
            case Update   => counts.copy(updates = counts.updates + 1)
            case Redirect => counts.copy(redirects = counts.redirects + 1)
          }
          case SuccessfulDelete(_) => counts.copy(deletes = counts.deletes + 1)
        }
      )
    )
  }

  def pushCountsToString(pushCounts: PushCounts): String =
    pushCounts match {
      case PushCounts(updates, newFiles, failures, redirects, deletes)
        if updates == 0 && newFiles == 0 && failures == 0 && redirects == 0 && deletes == 0 =>
          "There was nothing to push."
      case PushCounts(updates, newFiles, failures, redirects, deletes) =>
        val reportClauses: scala.collection.mutable.ArrayBuffer[String] = ArrayBuffer()
        if (updates > 0)   reportClauses += s"Updated $updates file(s)."
        if (newFiles > 0)  reportClauses += s"Created $newFiles file(s)."
        if (failures > 0)  reportClauses += s"$failures operation(s) failed." // This includes both failed uploads and deletes.
        if (redirects > 0) reportClauses += s"Applied $redirects redirect(s)."
        if (deletes > 0)   reportClauses += s"Deleted $deletes files(s)."
        reportClauses.mkString(" ")
    }

  case class PushCounts(
                         updates: Int = 0, 
                         newFiles: Int = 0, 
                         failures: Int = 0, 
                         redirects: Int = 0, 
                         deletes: Int = 0
                         )
  type FinishedPushOperations = ParSeq[Either[ErrorReport, Either[PushFailureReport, PushSuccessReport]]]
  type PushReports = ParSeq[Either[ErrorReport, Future[Either[PushFailureReport, PushSuccessReport]]]]
  case class PushResult(threadPool: ExecutorService, uploadReports: PushReports)
  type ExitCode = Int

  trait CliArgs {
    import com.lexicalscope.jewel.cli.Option

    @Option def site: String
    @Option(longName = Array("config-dir")) def configDir: String

  }

  def main(args: Array[String]) {
    val cliArgs = CliFactory.parseArguments(classOf[CliArgs], args:_*)
    val errorOrPushStatus = loadSite(cliArgs.configDir + "/s3_website.yml", cliArgs.site)
      .right
      .map {
      implicit site =>
        val threadPool = newFixedThreadPool(site.config.concurrency_level)
        implicit val executor = fromExecutor(threadPool)
        implicit val s3Settings = S3Settings()
        implicit val cloudFrontSettings = CloudFrontSettings()
        val pushStatus = pushSite
        threadPool.shutdownNow()
        pushStatus
    }
    errorOrPushStatus.left foreach (err => fail(s"Could not load the site: ${err.reportMessage}"))
    System.exit(errorOrPushStatus.fold(_ => 1, pushStatus => pushStatus))
  }
}
