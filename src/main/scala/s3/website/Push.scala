package s3.website

import s3.website.model.Site._
import scala.concurrent.{ExecutionContextExecutor, Future, Await}
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
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
import s3.website.S3.FailedUpload
import scala.collection.mutable.ArrayBuffer

object Push {

  def pushSite(implicit site: Site, executor: ExecutionContextExecutor, s3ClientProvider: S3ClientProvider = S3.awsS3Client): Int = {
    println(s"Deploying ${site.rootDirectory}/* to ${site.config.s3_bucket}")

    val errorsOrReports = for {
      s3Files    <- resolveS3Files.right
      localFiles <- resolveLocalFiles.right
    } yield {
      val redirects = Redirect.resolveRedirects
      val deleteReports: PushReports = resolveDeletes(localFiles, s3Files, redirects)
        .map { s3File => new S3() delete s3File.s3Key }
        .map { Right(_) /* To make delete reports type-compatible with upload reports */ }
        .par
      deleteReports.tasksupport_=(new ForkJoinTaskSupport(new ForkJoinPool(site.config.concurrency_level)))
      val uploadReports: PushReports = (redirects.toStream.map(Right(_)) ++ resolveUploads(localFiles, s3Files))
        .map { errorOrUpload => errorOrUpload.right.map(new S3() upload ) }
        .par
      uploadReports.tasksupport_=(new ForkJoinTaskSupport(new ForkJoinPool(site.config.concurrency_level)))
      val z = uploadReports ++ deleteReports
      z
    }
    val errorsOrFinishedUploads: Either[Error, FinishedPushOperations] = errorsOrReports.right map {
      uploadReports => awaitForUploads(uploadReports)
    }
    afterUploadsFinished(errorsOrFinishedUploads)
  }

  def afterUploadsFinished(errorsOrFinishedUploads: Either[Error, FinishedPushOperations])(implicit site: Site): Int = {
    errorsOrFinishedUploads.right.foreach { finishedUploads =>
      val pushCounts = pushCountsToString(resolvePushCounts(finishedUploads))
      println(s"$pushCounts")
      println(s"Go visit: http://${site.config.s3_bucket}.${site.config.s3_endpoint.s3WebsiteHostname}")
    }
    errorsOrFinishedUploads.left foreach (err => println(s"Failed to push the site: ${err.message}"))
    errorsOrFinishedUploads.fold(
      _ => 1,
      finishedUploads => finishedUploads.foldLeft(0) { (memo, finishedUpload) =>
        memo + finishedUpload.fold(
          (error: Error) => 1,
          (failedOrSucceededUpload: Either[PushFailureReport, PushSuccessReport]) =>
            if (failedOrSucceededUpload.isLeft) 1 else 0
        )
      } min 1
    )
  }

  def awaitForUploads(uploadReports: PushReports)(implicit executor: ExecutionContextExecutor): FinishedPushOperations =
    uploadReports map (_.right.map {
      rep => Await.result(rep, 1 day)
    })

  def resolvePushCounts(implicit finishedOperations: FinishedPushOperations) = finishedOperations.foldLeft(PushCounts()) {
    (counts: PushCounts, uploadReport) => uploadReport.fold(
      (error: model.Error) => counts.copy(failures = counts.failures + 1),
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
        if (failures > 0)  reportClauses += s"$failures operations failed." // This includes both failed uploads and deletes.
        if (redirects > 0) reportClauses += s"Applied $redirects redirect(s)."
        if (deletes > 0)   reportClauses += s"Deleted $deletes files(s)."
        reportClauses.mkString(" ")
    }

  case class PushCounts(updates: Int = 0, newFiles: Int = 0, failures: Int = 0, redirects: Int = 0, deletes: Int = 0)
  type FinishedPushOperations = ParSeq[Either[model.Error, Either[PushFailureReport, PushSuccessReport]]]
  type PushReports = ParSeq[Either[model.Error, Future[Either[PushFailureReport, PushSuccessReport]]]]
  case class PushResult(threadPool: ExecutorService, uploadReports: PushReports)

  trait CliArgs {
    import com.lexicalscope.jewel.cli.Option

    @Option def site: String
    @Option(longName = Array("config-dir")) def configDir: String
    @Option def headless: Boolean // TODO use this

  }

  def main(args: Array[String]) {
    val cliArgs = CliFactory.parseArguments(classOf[CliArgs], args:_*)
    val errorOrPushStatus = loadSite(cliArgs.configDir + "/s3_website.yml", cliArgs.site)
      .right
      .map {
      implicit site =>
        val threadPool = newFixedThreadPool(site.config.concurrency_level)
        implicit val executor = fromExecutor(threadPool)
        val pushStatus = pushSite
        threadPool.shutdownNow()
        pushStatus
    }
    errorOrPushStatus.left foreach (err => println(s"Could not load the site: ${err.message}"))
    System.exit(errorOrPushStatus.fold(_ => 1, pushStatus => pushStatus))
  }
}
