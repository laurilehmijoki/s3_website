package s3.website

import s3.website.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model._
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContextExecutor, Future}
import com.amazonaws.services.s3.model.StorageClass.ReducedRedundancy
import scala.concurrent.duration.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS
import s3.website.S3.SuccessfulUpload
import s3.website.S3.SuccessfulDelete
import s3.website.S3.FailedUpload
import scala.Some
import s3.website.S3.FailedDelete
import s3.website.S3.S3Settings

class S3(implicit s3Settings: S3Settings, executor: ExecutionContextExecutor) {

  def upload(upload: Upload with UploadTypeResolved, a: Attempt = 1)
            (implicit config: Config, logger: Logger): Future[Either[FailedUpload, SuccessfulUpload]] =
    Future {
      val putObjectRequest = toPutObjectRequest(upload)
      s3Settings.s3Client(config) putObject putObjectRequest
      val report = SuccessfulUpload(upload, putObjectRequest)
      logger.info(report)
      Right(report)
    } recoverWith retry(a)(
      createFailureReport = error => FailedUpload(upload.s3Key, error),
      retryAction  = newAttempt => this.upload(upload, newAttempt)
    )

  def delete(s3Key: String,  a: Attempt = 1)
            (implicit config: Config, logger: Logger): Future[Either[FailedDelete, SuccessfulDelete]] =
    Future {
      s3Settings.s3Client(config) deleteObject(config.s3_bucket, s3Key)
      val report = SuccessfulDelete(s3Key)
      logger.info(report)
      Right(report)
    } recoverWith retry(a)(
      createFailureReport = error => FailedDelete(s3Key, error),
      retryAction  = newAttempt => this.delete(s3Key, newAttempt)
    )
      
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
        uploadBody.maxAge foreach { seconds =>
          md.setCacheControl(
            if (seconds == 0)
              s"no-cache; max-age=$seconds"
            else
              s"max-age=$seconds"
          )
        }
        val req = new PutObjectRequest(config.s3_bucket, upload.s3Key, uploadBody.openInputStream(), md)
        config.s3_reduced_redundancy.filter(_ == true) foreach (_ => req setStorageClass ReducedRedundancy)
        req
      }
    )
}

object S3 {
  def awsS3Client(config: Config) = new AmazonS3Client(new BasicAWSCredentials(config.s3_id, config.s3_secret))

  def resolveS3FilesAndUpdates(localFiles: Seq[LocalFile])
                              (nextMarker: Option[String] = None, alreadyResolved: Seq[S3File] = Nil,  attempt: Attempt = 1, onFlightUpdateFutures: UpdateFutures = Nil)
                              (implicit config: Config, s3Settings: S3Settings, ec: ExecutionContextExecutor, logger: Logger):
  ErrorOrS3FilesAndUpdates = Future {
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
    val summaryIndex = objects.getObjectSummaries.map { summary => (summary.getETag, summary.getKey) }.toSet // Index to avoid O(n^2) lookups 
    def isUpdate(lf: LocalFile) =
      summaryIndex.exists((md5AndS3Key) => 
        md5AndS3Key._1 != lf.md5 && md5AndS3Key._2 == lf.s3Key
      )
    val updateFutures: UpdateFutures = localFiles.collect {
      case lf: LocalFile if isUpdate(lf) =>
        val errorOrUpdate = LocalFile
          .toUpload(lf)
          .right
          .map { (upload: Upload) =>
            upload.withUploadType(Update)
          }
        errorOrUpdate.right.map(update => new S3 upload update)
    }

    (objects, onFlightUpdateFutures ++ updateFutures)
  } flatMap { (objectsAndUpdateFutures) =>
    val objects: ObjectListing = objectsAndUpdateFutures._1
    val updateFutures: UpdateFutures = objectsAndUpdateFutures._2
    val s3Files = alreadyResolved ++ (objects.getObjectSummaries.toIndexedSeq.toSeq map (S3File(_)))
    Option(objects.getNextMarker)
      .fold(Future(Right((Right(s3Files), updateFutures))): ErrorOrS3FilesAndUpdates) // We've received all the S3 keys from the bucket
      { nextMarker => // There are more S3 keys on the bucket. Fetch them.
        resolveS3FilesAndUpdates(localFiles)(Some(nextMarker), s3Files, attempt = attempt, updateFutures)
      }
  } recoverWith retry(attempt)(
    createFailureReport = error => ClientError(s"Failed to fetch an object listing (${error.getMessage})"),
    retryAction = nextAttempt => resolveS3FilesAndUpdates(localFiles)(nextMarker, alreadyResolved, nextAttempt, onFlightUpdateFutures)
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

  case class SuccessfulUpload(upload: Upload with UploadTypeResolved, putObjectRequest: PutObjectRequest) extends PushSuccessReport {
    val metadata = putObjectRequest.getMetadata
    def metadataReport =
      (metadata.getCacheControl :: metadata.getContentType :: metadata.getContentEncoding :: putObjectRequest.getStorageClass :: Nil)
        .filterNot(_ == null)
        .mkString(" | ")

    def reportMessage =
      upload.uploadType match {
        case NewFile  => s"Created ${upload.s3Key} ($metadataReport)"
        case Update   => s"Updated ${upload.s3Key} ($metadataReport)"
        case Redirect => s"Redirected ${upload.essence.left.get.key} to ${upload.essence.left.get.redirectTarget}"
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
    retryTimeUnit: TimeUnit = SECONDS
  ) extends RetrySettings
}
