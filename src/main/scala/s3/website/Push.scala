package s3.website

import s3.website.model.Site._
import scala.concurrent.{ExecutionContextExecutor, Future, Await}
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.duration._
import com.lexicalscope.jewel.cli.CliFactory
import scala.language.postfixOps
import s3.website.Diff.resolveDiff
import s3.website.S3.{S3ClientProvider, resolveS3Files, SuccessfulUpload, FailedUpload}
import scala.concurrent.ExecutionContext.fromExecutor
import java.util.concurrent.Executors.newFixedThreadPool
import s3.website.model.LocalFile.resolveLocalFiles
import scala.collection.parallel.ParSeq
import java.util.concurrent.ExecutorService
import s3.website.model._
import s3.website.Implicits._
import s3.website.model.Update
import s3.website.model.NewFile

object Push {

  def pushSite(implicit site: Site, executor: ExecutionContextExecutor, s3ClientProvider: S3ClientProvider = S3.awsS3Client): Int = {
    println(s"Deploying ${site.rootDirectory}/* to ${site.config.s3_bucket}")

    val errorsOrUploadReports = for {
      s3Files    <- resolveS3Files.right
      localFiles <- resolveLocalFiles.right
    } yield {
      val redirects = Redirect.resolveRedirects
      val uploadReports = (redirects.toStream.map(Right(_)) ++ resolveDiff(localFiles, s3Files))
        .map { errorOrUpload => errorOrUpload.right.map(new S3() upload ) }
        .par
      uploadReports.tasksupport_=(new ForkJoinTaskSupport(new ForkJoinPool(site.config.concurrency_level)))
      uploadReports
    }
    errorsOrUploadReports.right foreach { uploadReports => reportEachUpload(uploadReports)}
    val errorsOrFinishedUploads = errorsOrUploadReports.right map { 
      uploadReports => awaitForUploads(uploadReports)
    }
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
          (failedOrSucceededUpload: Either[FailedUpload, SuccessfulUpload]) =>
            if (failedOrSucceededUpload.isLeft) 1 else 0
        )
      } min 1
    )
  }

  def reportEachUpload(uploadReports: UploadReports)(implicit executor: ExecutionContextExecutor) {
    uploadReports foreach {_.right.foreach(_ foreach { report =>
      println(
        report fold(_.reportMessage, _.reportMessage)
      )
    })}
  }
  
  def awaitForUploads(uploadReports: UploadReports)(implicit executor: ExecutionContextExecutor): FinishedUploads =
    uploadReports map (_.right.map {
      rep => Await.result(rep, 1 day)
    })

  def resolvePushCounts(implicit finishedUploads: FinishedUploads) = finishedUploads.foldLeft(PushCounts()) {
    (counts: PushCounts, uploadReport) => uploadReport.fold(
      (error: model.Error) => counts.copy(failures = counts.failures + 1),
      failureOrSuccess => failureOrSuccess.fold(
        (failedUpload: FailedUpload) => counts.copy(failures = counts.failures + 1),
        (success: SuccessfulUpload)  => success.upload.uploadType match {
          case NewFile  => counts.copy(newFiles = counts.newFiles + 1)
          case Update   => counts.copy(updates = counts.updates + 1)
          case Redirect => counts.copy(redirects = counts.redirects + 1)
        }
      )
    )
  }

  def pushCountsToString(pushCounts: PushCounts): String =
    pushCounts match {
      case PushCounts(updates, newFiles, failures, redirects) if updates == 0 && newFiles == 0 && failures == 0 && redirects == 0 =>
        "There was nothing to push."
      case PushCounts(updates, newFiles, failures, redirects) if updates > 0 && newFiles == 0 && failures == 0 && redirects == 0 =>
        s"Updated $updates file(s)."
      case PushCounts(updates, newFiles, failures, redirects) if updates == 0 && newFiles >= 0 && failures == 0 && redirects == 0 =>
        s"Created $newFiles file(s)."
      case PushCounts(updates, newFiles, failures, redirects) if updates > 0 && newFiles > 0 && failures == 0 && redirects == 0 =>
        s"Created $newFiles and updated $updates file(s)."
      case PushCounts(updates, newFiles, failures, redirects) if failures == 0 =>
        s"Created $newFiles and updated $updates file(s). Applied $redirects redirect(s)."
      case PushCounts(updates, newFiles, failures, redirects) =>
        s"Created $newFiles and updated $updates file(s). $failures upload(s) failed!"
    }

  case class PushCounts(updates: Int = 0, newFiles: Int = 0, failures: Int = 0, redirects: Int = 0/*, deletes: Int = -1 TODO implement delete*/)
  type FinishedUploads = ParSeq[Either[model.Error, Either[FailedUpload, SuccessfulUpload]]]
  type UploadReports = ParSeq[Either[model.Error, Future[Either[FailedUpload, SuccessfulUpload]]]]
  case class PushResult(threadPool: ExecutorService, uploadReports: UploadReports)

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
