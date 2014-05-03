package s3.website

import s3.website.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.{PutObjectRequest, ObjectMetadata, ListObjectsRequest, ObjectListing}
import scala.collection.JavaConversions._
import scala.util.Try
import com.amazonaws.AmazonClientException
import scala.util.Success
import scala.util.Failure
import scala.Some
import scala.concurrent.{ExecutionContextExecutor, Future}
import s3.website.model.{UserError, IOError}
import s3.website.S3.{S3ClientProvider, SuccessfulUpload, FailedUpload}

class S3(implicit s3Client: S3ClientProvider) {

  def upload(upload: Upload with UploadTypeResolved)(implicit config: Config, executor: ExecutionContextExecutor): Future[Either[FailedUpload, SuccessfulUpload]] =
    Future {
      s3Client(config) putObject toPutObjectRequest(upload)
      Right(SuccessfulUpload(upload))
    } recover {
      case error => Left(FailedUpload(upload.s3Key, error))
    }

  def toPutObjectRequest(upload: Upload)(implicit config: Config) =
    upload.essence.fold(
      redirect => {
        val req = new PutObjectRequest(config.s3_bucket, upload.s3Key, redirect.redirectTarget)
        req.setMetadata({
          val md = new ObjectMetadata()
          md.setContentLength(0) // Otherwise the AWS SDK will log a warning
          md
        })
        req
      },
      uploadBody => {
        val md = new ObjectMetadata()
        md.setContentLength(uploadBody.contentLength)
        md.setContentType(uploadBody.contentType)
        md.setContentMD5(uploadBody.md5)
        uploadBody.contentEncoding.foreach(md.setContentEncoding)
        uploadBody.maxAge.foreach(seconds => md.setCacheControl(s"max-age=$seconds"))
        new PutObjectRequest(config.s3_bucket, upload.s3Key, uploadBody.openInputStream(), md)
      }
    )
}

object S3 {
  def awsS3Client(config: Config) = new AmazonS3Client(new BasicAWSCredentials(config.s3_id, config.s3_secret))

  def resolveS3Files(implicit config: Config, s3ClientProvider: S3ClientProvider): Either[Error, Seq[S3File]] = Try {
    objectSummaries()
  } match {
    case Success(remoteFiles) =>
      Right(remoteFiles)
    case Failure(error) if error.isInstanceOf[AmazonClientException] =>
      Left(UserError(error.getMessage))
    case Failure(error) =>
      Left(IOError(error))
  }

  def objectSummaries(nextMarker: Option[String] = None)(implicit config: Config, s3ClientProvider: S3ClientProvider): Seq[S3File] = {
    val objects: ObjectListing = s3ClientProvider(config).listObjects({
      val req = new ListObjectsRequest()
      req.setBucketName(config.s3_bucket)
      nextMarker.foreach(req.setMarker)
      req
    })
    val summaries = objects.getObjectSummaries map (S3File(_))
    if (objects.isTruncated)
      summaries ++ objectSummaries(Some(objects.getNextMarker))
    else
      summaries
  }

  trait UploadReport {
    def reportMessage: String
  }

  case class SuccessfulUpload(upload: Upload with UploadTypeResolved) extends UploadReport {
    def reportMessage =
      upload.uploadType match {
        case NewFile  => s"Created ${upload.s3Key}"
        case Update   => s"Updated ${upload.s3Key}"
        case Redirect => s"Redirecting ${upload.essence.left.get.key} to ${upload.essence.left.get.redirectTarget}"
      }
  }

  case class FailedUpload(s3Key: String, error: Throwable) extends UploadReport {
    def reportMessage = s"Failed to upload $s3Key (${error.getMessage})"
  }

  type S3ClientProvider = (Config) => AmazonS3
}
