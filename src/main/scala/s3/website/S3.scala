package s3.website

import s3.website.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model._
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContextExecutor, Future}
import com.amazonaws.services.s3.model.StorageClass.ReducedRedundancy
import s3.website.S3.SuccessfulUpload
import s3.website.S3.SuccessfulDelete
import s3.website.S3.FailedUpload
import scala.Some
import s3.website.S3.FailedDelete
import s3.website.S3.S3Setting
import s3.website.ByteHelper.humanReadableByteCount
import org.joda.time.{Seconds, Duration, Interval}
import scala.concurrent.duration.TimeUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS
import s3.website.S3.SuccessfulUpload.humanizeUploadSpeed
import java.io.FileInputStream

class S3(implicit s3Settings: S3Setting, pushMode: PushMode, executor: ExecutionContextExecutor, logger: Logger) {

  def upload(source: Either[LocalFileFromDisk, Redirect], a: Attempt = 1)
            (implicit config: Config): Future[Either[FailedUpload, SuccessfulUpload]] =
    Future {
      val putObjectRequest = toPutObjectRequest(source)
      val uploadDuration =
        if (pushMode.dryRun) None
        else recordUploadDuration(putObjectRequest, s3Settings.s3Client(config) putObject putObjectRequest)
      val report = SuccessfulUpload(source, putObjectRequest, uploadDuration)
      logger.info(report)
      Right(report)
    } recoverWith retry(a)(
      createFailureReport = error => FailedUpload(source.fold(_.s3Key, _.s3Key), error),
      retryAction  = newAttempt => this.upload(source, newAttempt)
    )

  def delete(s3File: S3File,  a: Attempt = 1)
            (implicit config: Config): Future[Either[FailedDelete, SuccessfulDelete]] =
    Future {
      if (!pushMode.dryRun) s3Settings.s3Client(config) deleteObject(config.s3_bucket, s3File.s3Key)
      val report = SuccessfulDelete(s3File.s3Key)
      logger.info(report)
      Right(report)
    } recoverWith retry(a)(
      createFailureReport = error => FailedDelete(s3File.s3Key, error),
      retryAction  = newAttempt => this.delete(s3File, newAttempt)
    )

  def toPutObjectRequest(source: Either[LocalFileFromDisk, Redirect])(implicit config: Config) =
    source.fold(
      localFile => {
        val md = new ObjectMetadata()
        md setContentLength localFile.uploadFile.length
        md setContentType localFile.contentType
        localFile.encodingOnS3.map(_ => "gzip") foreach md.setContentEncoding
        localFile.maxAge foreach { seconds =>
          md.setCacheControl(
            if (seconds == 0)
              s"no-cache; max-age=$seconds"
            else
              s"max-age=$seconds"
          )
        }
        val req = new PutObjectRequest(config.s3_bucket, localFile.s3Key, new FileInputStream(localFile.uploadFile), md)
        config.s3_reduced_redundancy.filter(_ == true) foreach (_ => req setStorageClass ReducedRedundancy)
        req
      },
      redirect => {
        val req = new PutObjectRequest(config.s3_bucket, redirect.s3Key, redirect.redirectTarget)
        req.setMetadata({
          val md = new ObjectMetadata()
          md.setContentLength(0) // Otherwise the AWS SDK will log a warning
          /*
           * Instruct HTTP clients to always re-check the redirect. The 301 status code may override this, though.
           * This is for the sake of simplicity.
           */
          md.setCacheControl("max-age=0, no-cache")
          md
        })
        req
      }
    )

  def recordUploadDuration(putObjectRequest: PutObjectRequest, f: => Unit): Option[Duration] = {
    val start = System.currentTimeMillis()
    f
    if (putObjectRequest.getMetadata.getContentLength > 0)
      Some(new Duration(start, System.currentTimeMillis))
    else
      None // We are not interested in tracking durations of PUT requests that don't contain data. Redirect is an example of such request.
  }
}

object S3 {
  def awsS3Client(config: Config) = new AmazonS3Client(new BasicAWSCredentials(config.s3_id, config.s3_secret))

  def resolveS3Files(nextMarker: Option[String] = None, alreadyResolved: Seq[S3File] = Nil,  attempt: Attempt = 1)
                              (implicit config: Config, s3Settings: S3Setting, ec: ExecutionContextExecutor, logger: Logger, pushMode: PushMode):
  Future[Either[ErrorReport, Seq[S3File]]] = Future {
    logger.debug(nextMarker.fold
      ("Querying S3 files")
      {m => s"Querying more S3 files (starting from $m)"}
    )
    val objects: ObjectListing = s3Settings.s3Client(config).listObjects({
      val req = new ListObjectsRequest()
      req.setBucketName(config.s3_bucket)
      nextMarker.foreach(req.setMarker)
      req
    })
    objects
  } flatMap { (objects: ObjectListing) =>
    val s3Files = alreadyResolved ++ (objects.getObjectSummaries.toIndexedSeq.toSeq map (S3File(_)))
    Option(objects.getNextMarker)
      .fold(Future(Right(s3Files)): Future[Either[ErrorReport, Seq[S3File]]]) // We've received all the S3 keys from the bucket
      { nextMarker => // There are more S3 keys on the bucket. Fetch them.
        resolveS3Files(Some(nextMarker), s3Files, attempt = attempt)
      }
  } recoverWith retry(attempt)(
    createFailureReport = error => ErrorReport(s"Failed to fetch an object listing (${error.getMessage})"),
    retryAction = nextAttempt => resolveS3Files(nextMarker, alreadyResolved, nextAttempt)
  )

  type S3FilesAndUpdates = (ErrorOrS3Files, UpdateFutures)
  type S3FilesAndUpdatesFuture = Future[S3FilesAndUpdates]
  type ErrorOrS3FilesAndUpdates = Future[Either[ErrorReport, S3FilesAndUpdates]]
  type UpdateFutures = Seq[Either[ErrorReport, Future[PushErrorOrSuccess]]]
  type ErrorOrS3Files = Either[ErrorReport, Seq[S3File]]

  sealed trait PushFailureReport extends FailureReport
  sealed trait PushSuccessReport extends SuccessReport {
    def s3Key: String
  }

  case class SuccessfulUpload(source: Either[LocalFileFromDisk, Redirect], putObjectRequest: PutObjectRequest, uploadDuration: Option[Duration])
                             (implicit pushMode: PushMode, logger: Logger) extends PushSuccessReport {
    def reportMessage =
      source.fold(_.uploadType, (redirect: Redirect) => redirect) match {
        case NewFile                         => s"${Created.renderVerb} $s3Key ($reportDetails)"
        case FileUpdate                      => s"${Updated.renderVerb} $s3Key ($reportDetails)"
        case Redirect(s3Key, redirectTarget) => s"${Redirected.renderVerb} $s3Key to $redirectTarget"
      }

    def reportDetails = {
      val md = putObjectRequest.getMetadata
      val detailFragments: Seq[Option[String]] =
        (
          md.getCacheControl ::
          md.getContentType ::
          md.getContentEncoding ::
          putObjectRequest.getStorageClass ::
          Nil map (Option(_)) // AWS SDK may return nulls
        ) :+ uploadSizeForHumans :+ uploadSpeedForHumans
      detailFragments.collect {
        case Some(detailFragment) => detailFragment
      }.mkString(" | ")
    }

    def s3Key = source.fold(_.s3Key, _.s3Key)

    lazy val uploadSize: Option[Long] =
      source.fold(
        (localFile: LocalFileFromDisk) => Some(localFile.uploadFile.length()),
        (redirect: Redirect)           => None
    )

    lazy val uploadSizeForHumans: Option[String] = uploadSize filter (_ => logger.verboseOutput) map humanReadableByteCount

    lazy val uploadSpeedForHumans: Option[String] =
      (for {
        dataSize <- uploadSize
        duration <- uploadDuration
      } yield {
        humanizeUploadSpeed(dataSize, duration)
      }) flatMap identity filter (_ => logger.verboseOutput)
  }
  
  object SuccessfulUpload {
    def humanizeUploadSpeed(uploadedBytes: Long, uploadDurations: Duration*): Option[String] = {
      val totalDurationMillis = uploadDurations.foldLeft(new org.joda.time.Duration(0)){ (memo, duration) =>
        memo.plus(duration)
      }.getMillis // retain precision by using milliseconds
      if (totalDurationMillis > 0) {
        val bytesPerMillisecond = uploadedBytes / totalDurationMillis
        val bytesPerSecond = bytesPerMillisecond * 1000 * uploadDurations.length
        Some(humanReadableByteCount(bytesPerSecond) + "/s")  
      } else {
        None
      }
    }
  }

  case class SuccessfulDelete(s3Key: String)(implicit pushMode: PushMode) extends PushSuccessReport {
    def reportMessage = s"${Deleted.renderVerb} $s3Key"
  }

  case class FailedUpload(s3Key: String, error: Throwable) extends PushFailureReport {
    def reportMessage = s"Failed to upload $s3Key (${error.getMessage})"
  }

  case class FailedDelete(s3Key: String, error: Throwable) extends PushFailureReport {
    def reportMessage = s"Failed to delete $s3Key (${error.getMessage})"
  }

  type S3ClientProvider = (Config) => AmazonS3

  case class S3Setting(
    s3Client: S3ClientProvider = S3.awsS3Client,
    retryTimeUnit: TimeUnit = SECONDS
  ) extends RetrySetting
}
