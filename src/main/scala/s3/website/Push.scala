package s3.website

import s3.website.model.Site._
import scala.concurrent.{ExecutionContextExecutor, Future, Await}
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.duration._
import com.lexicalscope.jewel.cli.CliFactory
import scala.language.postfixOps
import s3.website.Diff.resolveDiff
import s3.website.S3.{FailedUpload, SuccessfulUpload, resolveS3Files, upload}
import scala.concurrent.ExecutionContext.fromExecutor
import java.util.concurrent.Executors.newFixedThreadPool
import s3.website.model.LocalFile.resolveLocalFiles
import scala.collection.parallel.ParSeq
import java.util.concurrent.ExecutorService
import s3.website.model.{Update, NewFile, Site}

object Push {

  def pushSite(implicit site: Site, executor: ExecutionContextExecutor): Int = {
    println(s"Deploying ${site.rootDirectory}/* to ${site.config.s3_bucket}")

    val errorsOrUploadReports = for {
      s3Files    <- resolveS3Files.right
      localFiles <- resolveLocalFiles.right
    } yield {
      val uploadReports = resolveDiff(localFiles, s3Files).map(_.right.map(upload)).par
      uploadReports.tasksupport_=(new ForkJoinTaskSupport(new ForkJoinPool(site.config.concurrency_level)))
      uploadReports
    }
    errorsOrUploadReports.right foreach { uploadReports => reportEachUpload(uploadReports)}
    val errorsOrFinishedUploads = errorsOrUploadReports.right map { 
      uploadReports => awaitForUploads(uploadReports)
    }
    errorsOrFinishedUploads.right.foreach { finishedUploads =>
      println(pushCountsToString(resolvePushCounts(finishedUploads)))
    }
    errorsOrUploadReports.left foreach (err => println(s"Failed to push the site: ${err.message}"))
    errorsOrUploadReports.right foreach (err => println(s"Done! Go visit: http://${site.config.s3_bucket}.${site.config.s3_endpoint.s3WebsiteHostname}"))
    errorsOrUploadReports.fold(
      _ => 1,
      uploadReports => if (uploadReports exists (_.isLeft)) 1 else 0
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
        (success: SuccessfulUpload)  => success.uploadSource.uploadType.fold(
          (newFile: NewFile) => counts.copy(newFiles = counts.newFiles + 1),
          (update: Update)   => counts.copy(updates = counts.updates + 1)
        )
      )
    )
  }

  def pushCountsToString(pushCounts: PushCounts): String =
    pushCounts match {
      case PushCounts(updates, newFiles, failures) if updates == 0 && newFiles == 0 && failures == 0 =>
        "No new or changed files to upload"
      case PushCounts(updates, newFiles, failures) if updates > 0 && newFiles == 0 && failures == 0 =>
        s"Updated $updates files"
      case PushCounts(updates, newFiles, failures) if updates == 0 && newFiles >= 0 && failures == 0 =>
        s"Created $newFiles files"
      case PushCounts(updates, newFiles, failures) if updates > 0 && newFiles > 0 && failures == 0 =>
        s"Created $newFiles and updated $updates files"
      case PushCounts(updates, newFiles, failures) =>
        s"Created $newFiles and updated $updates files. $failures uploads failed!"
    }

  case class PushCounts(updates: Int = 0, newFiles: Int = 0, failures: Int = 0/*, deletes: Int = -1 TODO implement delete*/)
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
