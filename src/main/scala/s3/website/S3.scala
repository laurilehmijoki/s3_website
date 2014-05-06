package s3.website

import s3.website.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model._
import scala.collection.JavaConversions._
import scala.util.Try
import com.amazonaws.AmazonClientException
import scala.concurrent.{ExecutionContextExecutor, Future}
import s3.website.S3._
import com.amazonaws.services.s3.model.StorageClass.ReducedRedundancy
import s3.website.S3.SuccessfulUpload
import s3.website.S3.FailedUpload
import scala.util.Failure
import scala.Some
import s3.website.model.IOError
import scala.util.Success
import s3.website.model.UserError

class S3(implicit s3Client: S3ClientProvider) {

  def upload(upload: Upload with UploadTypeResolved)(implicit config: Config, executor: ExecutionContextExecutor): Future[Either[FailedUpload, SuccessfulUpload]] =
    Future {
      s3Client(config) putObject toPutObjectRequest(upload)
      val report = SuccessfulUpload(upload)
      println(report.reportMessage)
      Right(report)
    } recover {
      case error =>
        val report = FailedUpload(upload.s3Key, error)
        println(report.reportMessage)
        Left(report)
    }

  def delete(s3Key: String)(implicit config: Config, executor: ExecutionContextExecutor): Future[Either[FailedDelete, SuccessfulDelete]] =
    Future {
      s3Client(config) deleteObject(config.s3_bucket, s3Key)
      val report = SuccessfulDelete(s3Key)
      println(report.reportMessage)
      Right(report)
    } recover {
      case error =>
        val report = FailedDelete(s3Key, error)
        println(report.reportMessage)
        Left(report)
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

  def resolveS3Files(implicit config: Config, s3ClientProvider: S3ClientProvider): Either[Error, Stream[S3File]] = Try {
    objectSummaries()
  } match {
    case Success(remoteFiles) =>
      Right(remoteFiles)
    case Failure(error) if error.isInstanceOf[AmazonClientException] =>
      Left(UserError(error.getMessage))
    case Failure(error) =>
      Left(IOError(error))
  }

  def objectSummaries(nextMarker: Option[String] = None)(implicit config: Config, s3ClientProvider: S3ClientProvider): Stream[S3File] = {
    val objects: ObjectListing = s3ClientProvider(config).listObjects({
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

  sealed trait PushItemReport {
    def reportMessage: String
  }

  sealed trait PushFailureReport extends PushItemReport
  sealed trait PushSuccessReport extends PushItemReport {
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
}
