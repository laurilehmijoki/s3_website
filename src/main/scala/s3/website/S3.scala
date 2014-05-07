package s3.website

import s3.website.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model._
import scala.collection.JavaConversions._
import scala.util.Try
import com.amazonaws.{AmazonServiceException, AmazonClientException}
import scala.concurrent.{ExecutionContextExecutor, Future}
import s3.website.S3._
import com.amazonaws.services.s3.model.StorageClass.ReducedRedundancy
import s3.website.Logger._
import s3.website.Utils._
import scala.concurrent.duration.{TimeUnit, Duration}
import java.util.concurrent.TimeUnit.SECONDS
import s3.website.S3.SuccessfulUpload
import s3.website.S3.SuccessfulDelete
import s3.website.S3.FailedUpload
import scala.util.Failure
import scala.Some
import s3.website.S3.FailedDelete
import s3.website.model.IOError
import s3.website.S3.S3Settings
import scala.util.Success
import s3.website.model.UserError
import com.amazonaws.AmazonServiceException.ErrorType
import s3.website.model.Error.isClientError

class S3(implicit s3Settings: S3Settings, executor: ExecutionContextExecutor) {

  def upload(upload: Upload with UploadTypeResolved)(implicit a: Attempt = 1, config: Config): S3PushResult =
    Future {
      s3Settings.s3Client(config) putObject toPutObjectRequest(upload)
      val report = SuccessfulUpload(upload)
      info(report)
      Right(report)
    } recoverWith retry(
      createReport = error => FailedUpload(upload.s3Key, error),
      retryAction  = newAttempt => this.upload(upload)(newAttempt, config)
    )

  def delete(s3Key: String)(implicit a: Attempt = 1, config: Config): S3PushResult =
    Future {
      s3Settings.s3Client(config) deleteObject(config.s3_bucket, s3Key)
      val report = SuccessfulDelete(s3Key)
      info(report)
      Right(report)
    } recoverWith retry(
      createReport = error => FailedDelete(s3Key, error),
      retryAction  = newAttempt => this.delete(s3Key)(newAttempt, config)
    )
      
  type S3PushResult = Future[Either[PushFailureReport, PushSuccessReport]]
  
  type Attempt = Int
  
  def retry(createReport: (Throwable) => PushFailureReport, retryAction: (Attempt) => S3PushResult)(implicit attempt: Attempt):
  PartialFunction[Throwable, S3PushResult] = {
    case error: Throwable if attempt == 6 || isClientError(error) =>
      val failureReport = createReport(error)
      info(failureReport)
      Future(Left(failureReport))
    case error: Throwable =>
      val failureReport = createReport(error)
      val sleepDuration = Duration(fibs.drop(attempt + 1).head, s3Settings.retrySleepTimeUnit)
      pending(s"${failureReport.reportMessage}. Trying again in $sleepDuration.")
      Thread.sleep(sleepDuration.toMillis)
      retryAction(attempt + 1)
  }

  def toPutObjectRequest(upload: Upload)(implicit config: Config) =
    upload.essence.fold(
      redirect => {
        val req = new PutObjectRequest(config.s3_bucket, upload.s3Key, redirect.redirectTarget)
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
      },
      uploadBody => {
        val md = new ObjectMetadata()
        md setContentLength uploadBody.contentLength
        md setContentType uploadBody.contentType
        uploadBody.contentEncoding foreach md.setContentEncoding
        uploadBody.maxAge foreach (seconds => md.setCacheControl(s"max-age=$seconds"))
        val req = new PutObjectRequest(config.s3_bucket, upload.s3Key, uploadBody.openInputStream(), md)
        config.s3_reduced_redundancy.filter(_ == true) foreach (_ => req setStorageClass ReducedRedundancy)
        req
      }
    )
}

object S3 {
  def awsS3Client(config: Config) = new AmazonS3Client(new BasicAWSCredentials(config.s3_id, config.s3_secret))

  def resolveS3Files(implicit config: Config, s3Settings: S3Settings): Either[Error, Stream[S3File]] = Try {
    objectSummaries()
  } match {
    case Success(remoteFiles) =>
      Right(remoteFiles)
    case Failure(error) if error.isInstanceOf[AmazonClientException] =>
      Left(UserError(error.getMessage))
    case Failure(error) =>
      Left(IOError(error))
  }

  def objectSummaries(nextMarker: Option[String] = None)(implicit config: Config, s3Settings: S3Settings): Stream[S3File] = {
    val objects: ObjectListing = s3Settings.s3Client(config).listObjects({
      val req = new ListObjectsRequest()
      req.setBucketName(config.s3_bucket)
      nextMarker.foreach(req.setMarker)
      req
    })
    val summaries = (objects.getObjectSummaries map (S3File(_))).toStream
    if (objects.isTruncated)
      summaries #::: objectSummaries(Some(objects.getNextMarker)) // Call the next Get Bucket request lazily
    else
      summaries
  }

  sealed trait PushFailureReport extends FailureReport
  sealed trait PushSuccessReport extends SuccessReport {
    def s3Key: String
  }

  case class SuccessfulUpload(upload: Upload with UploadTypeResolved) extends PushSuccessReport {
    def reportMessage =
      upload.uploadType match {
        case NewFile  => s"Created ${upload.s3Key}"
        case Update   => s"Updated ${upload.s3Key}"
        case Redirect => s"Redirecting ${upload.essence.left.get.key} to ${upload.essence.left.get.redirectTarget}"
      }

    def s3Key = upload.s3Key
  }

  case class SuccessfulDelete(s3Key: String) extends PushSuccessReport {
    def reportMessage = s"Deleted $s3Key"
  }

  case class FailedUpload(s3Key: String, error: Throwable) extends PushFailureReport {
    def reportMessage = s"Failed to upload $s3Key (${error.getMessage})"
  }

  case class FailedDelete(s3Key: String, error: Throwable) extends PushFailureReport {
    def reportMessage = s"Failed to delete $s3Key (${error.getMessage})"
  }

  type S3ClientProvider = (Config) => AmazonS3

  case class S3Settings(
    s3Client: S3ClientProvider = S3.awsS3Client,
    retrySleepTimeUnit: TimeUnit = SECONDS
  )
}
