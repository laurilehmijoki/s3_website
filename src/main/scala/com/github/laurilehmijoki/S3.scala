package com.github.laurilehmijoki

import com.github.laurilehmijoki.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest, ListObjectsRequest, ObjectListing}
import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}
import com.amazonaws.AmazonClientException
import com.github.laurilehmijoki.model.IOError
import scala.util.Success
import com.github.laurilehmijoki.model.UserError
import scala.util.Failure
import scala.Some
import scala.concurrent.{ExecutionContextExecutor, Future}
import org.apache.tika.Tika
import org.apache.commons.codec.binary.Base64
import com.amazonaws.services.s3.transfer.{Upload, TransferManager}

object S3 {

  case class SuccessfulUpload(s3Key: String)
  case class FailedUpload(s3Key: String, error: Throwable)

  def upload(uploadSource: UploadSource)(implicit site: Site, executor: ExecutionContextExecutor): Future[Either[FailedUpload, SuccessfulUpload]] =
    Future {
      val objectMetadata = {
        val metaData = new ObjectMetadata()
        uploadSource.contentEncoding.foreach(metaData.setContentEncoding)
        metaData.setContentLength(uploadSource.contentLength)
        metaData.setContentType(uploadSource.contentType)
        metaData
      }
      s3Client.putObject(
        site.config.s3_bucket, uploadSource.s3Key, uploadSource.openInputStream(), objectMetadata
      )
      Right(SuccessfulUpload(uploadSource.s3Key))
    } recover {
      case error => Left(FailedUpload(uploadSource.s3Key, error))
    }

  def resolveS3Files(implicit site: Site): Either[Error, Seq[S3File]] = Try {
    objectSummaries()
  } match {
    case Success(remoteFiles) =>
      Right(remoteFiles)
    case Failure(error) if error.isInstanceOf[AmazonClientException] =>
      Left(UserError(error.getMessage))
    case Failure(error) =>
      Left(IOError(error))
  }

  def objectSummaries(nextMarker: Option[String] = None)(implicit site: Site): Seq[S3File] = {
    val objects: ObjectListing = s3Client.listObjects({
      val req = new ListObjectsRequest()
      req.setBucketName(site.config.s3_bucket)
      nextMarker.foreach(req.setMarker)
      req
    })
    val summaries = objects.getObjectSummaries map (S3File(_))
    if (objects.isTruncated)
      summaries ++ objectSummaries(Some(objects.getNextMarker))
    else
      summaries
  }

  def s3Client(implicit site: Site): AmazonS3Client =
    new AmazonS3Client(new BasicAWSCredentials(site.config.s3_id, site.config.s3_secret))

}
