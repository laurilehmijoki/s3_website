package s3.website

import s3.website.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.{ObjectMetadata, ListObjectsRequest, ObjectListing}
import scala.collection.JavaConversions._
import scala.util.Try
import com.amazonaws.AmazonClientException
import scala.util.Success
import scala.util.Failure
import scala.Some
import scala.concurrent.{ExecutionContextExecutor, Future}
import s3.website.model.{UserError, IOError}
import s3.website.S3.{SuccessfulUpload, FailedUpload}

class S3(s3Client: (Config) => AmazonS3 = S3.s3Client) {

  def upload(upload: Upload with UploadType)(implicit config: Config, executor: ExecutionContextExecutor): Future[Either[FailedUpload, SuccessfulUpload]] =
    Future {
      val objectMetadata = {
        val metaData = new ObjectMetadata()
        upload.contentEncoding.foreach(metaData.setContentEncoding)
        metaData.setContentLength(upload.contentLength)
        metaData.setContentType(upload.contentType)
        upload.maxAge.foreach(seconds => metaData.setCacheControl(s"max-age=$seconds"))
        metaData
      }
      s3Client(config).putObject(
        config.s3_bucket, upload.s3Key, upload.openInputStream(), objectMetadata
      )
      Right(SuccessfulUpload(upload))
    } recover {
      case error => Left(FailedUpload(upload.s3Key, error))
    }
}

object S3 {
  def s3Client(config: Config) = new AmazonS3Client(new BasicAWSCredentials(config.s3_id, config.s3_secret))

  def resolveS3Files(implicit config: Config): Either[Error, Seq[S3File]] = Try {
    objectSummaries()
  } match {
    case Success(remoteFiles) =>
      Right(remoteFiles)
    case Failure(error) if error.isInstanceOf[AmazonClientException] =>
      Left(UserError(error.getMessage))
    case Failure(error) =>
      Left(IOError(error))
  }

  def objectSummaries(nextMarker: Option[String] = None)(implicit config: Config): Seq[S3File] = {
    val objects: ObjectListing = s3Client(config).listObjects({
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

  case class SuccessfulUpload(upload: Upload with UploadType) extends UploadReport {
    def reportMessage = {
      val uploadDetail = upload.uploadType.fold(
        _ => "Created",
        _ => "Updated"
      )
      s"$uploadDetail ${upload.s3Key}"
    }
  }
  case class FailedUpload(s3Key: String, error: Throwable) extends UploadReport {
    def reportMessage = s"Failed to upload $s3Key (${error.getMessage})"
  }
}
