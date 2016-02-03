package s3.website

import s3.website.ErrorReport.errorMessage
import s3.website.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.amazonaws.services.s3.model._
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContextExecutor, Future}
import com.amazonaws.services.s3.model.StorageClass.ReducedRedundancy
import s3.website.ByteHelper.humanReadableByteCount
import scala.concurrent.duration.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS
import s3.website.S3.SuccessfulUpload.humanizeUploadSpeed
import java.io.FileInputStream
import s3.website.model.Config.awsCredentials
import scala.util.Try

object S3 {

  def uploadRedirect(redirect: Redirect, a: Attempt = 1)
            (implicit config: Config, s3Settings: S3Setting, pushOptions: PushOptions, executor: ExecutionContextExecutor, logger: Logger) =
    uploadToS3(Right(redirect))

  def uploadFile(up: Upload, a: Attempt = 1)
                    (implicit config: Config, s3Settings: S3Setting, pushOptions: PushOptions, executor: ExecutionContextExecutor, logger: Logger) =
    uploadToS3(Left(up))

  def uploadToS3(source: Either[Upload, Redirect], a: Attempt = 1)
            (implicit config: Config, s3Settings: S3Setting, pushOptions: PushOptions, executor: ExecutionContextExecutor, logger: Logger):
  Future[Either[FailedUpload, SuccessfulUpload]] =
    Future {
      val putObjectRequest = toPutObjectRequest(source).get
      val uploadDuration =
        if (pushOptions.dryRun) None
        else Some(recordUploadDuration(putObjectRequest, s3Settings.s3Client(config) putObject putObjectRequest))
      val report = SuccessfulUpload(
        source.fold(_.s3Key, _.s3Key),
        source.fold(
          upload => Left(SuccessfulNewOrCreatedDetails(upload.uploadType, upload.uploadFile.get.length(), uploadDuration)),
          redirect  => Right(SuccessfulRedirectDetails(redirect.uploadType, redirect.redirectTarget))
        ),
        putObjectRequest
      )
      logger.info(report)
      Right(report)
    } recoverWith retry(a)(
      createFailureReport = error => FailedUpload(source.fold(_.s3Key, _.s3Key), error),
      retryAction  = newAttempt => this.uploadToS3(source, newAttempt)
    )

  def delete(s3Key: S3Key,  a: Attempt = 1)
            (implicit config: Config, s3Settings: S3Setting, pushOptions: PushOptions, executor: ExecutionContextExecutor, logger: Logger):
  Future[Either[FailedDelete, SuccessfulDelete]] =
    Future {
      if (!pushOptions.dryRun) s3Settings.s3Client(config) deleteObject(config.s3_bucket, s3Key.key)
      val report = SuccessfulDelete(s3Key)
      logger.info(report)
      Right(report)
    } recoverWith retry(a)(
      createFailureReport = error => FailedDelete(s3Key, error),
      retryAction  = newAttempt => this.delete(s3Key, newAttempt)
    )

  def toPutObjectRequest(source: Either[Upload, Redirect])(implicit config: Config, logger: Logger): Try[PutObjectRequest] =
    source.fold(
      upload =>
        for {
          uploadFile <- upload.uploadFile
          contentType <- upload.contentType
        } yield {
          val md = new ObjectMetadata()
          md setContentLength uploadFile.length
          md setContentType contentType
          upload.encodingOnS3.map(_ => "gzip") foreach md.setContentEncoding
          val cacheControl: Option[String] = (upload.maxAge, upload.cacheControl) match {
            case (maxAge: Some[Int], cacheCtrl: Some[String]) =>
              logger.warn("Overriding the max_age setting with the cache_control setting")
              cacheCtrl
            case (_, cacheCtrl: Some[String]) =>
              cacheCtrl
            case (maxAgeSeconds: Some[int], None) =>
              maxAgeSeconds.map({
                case seconds if seconds == 0 => s"no-cache, max-age=0"
                case seconds                 => s"max-age=$seconds"
              })
            case (None, None) => None
          }
          cacheControl foreach { md.setCacheControl }
          val req = new PutObjectRequest(config.s3_bucket, upload.s3Key.key, new FileInputStream(uploadFile), md)
          config.s3_reduced_redundancy.filter(_ == true) foreach (_ => req setStorageClass ReducedRedundancy)
          req
        }
      ,
      redirect => {
        val req = new PutObjectRequest(config.s3_bucket, redirect.s3Key.key, redirect.redirectTarget)
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
        Try(req)
      }
    )

  def recordUploadDuration(putObjectRequest: PutObjectRequest, f: => Unit): UploadDuration = {
    val start = System.currentTimeMillis()
    f
    System.currentTimeMillis - start
  }

  def awsS3Client(config: Config) = new AmazonS3Client(awsCredentials(config))

  def resolveS3Files(nextMarker: Option[String] = None, alreadyResolved: Seq[S3File] = Nil,  attempt: Attempt = 1)
                              (implicit site: Site, s3Settings: S3Setting, ec: ExecutionContextExecutor, logger: Logger, pushOptions: PushOptions):
  Future[Either[ErrorReport, Seq[S3File]]] = Future {
    logger.debug(nextMarker.fold
      ("Querying S3 files")
      {m => s"Querying more S3 files (starting from $m)"}
    )
    val objects: ObjectListing = s3Settings.s3Client(site.config).listObjects({
      val req = new ListObjectsRequest()
      req.setBucketName(site.config.s3_bucket)
      nextMarker.foreach(req.setMarker)
      req
    })
    objects
  } flatMap { (objects: ObjectListing) =>

    /**
     * We could filter the keys by prefix already on S3, but unfortunately s3_website test infrastructure does not currently support testing of that.
     * Hence fetch all the keys from S3 and then filter by s3_key_prefix.
     */
    def matchesPrefix(os: S3ObjectSummary) = site.config.s3_key_prefix.fold(true)(prefix => os.getKey.startsWith(prefix))

    val s3Files = alreadyResolved ++ (objects.getObjectSummaries.filter(matchesPrefix).toIndexedSeq.toSeq map (S3File(_)))
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

  sealed trait PushFailureReport extends ErrorReport
  sealed trait PushSuccessReport extends SuccessReport {
    def s3Key: S3Key
  }

  case class SuccessfulRedirectDetails(uploadType: UploadType, redirectTarget: String)
  case class SuccessfulNewOrCreatedDetails(uploadType: UploadType, uploadSize: Long, uploadDuration: Option[Long])

  case class SuccessfulUpload(s3Key: S3Key,
                              details: Either[SuccessfulNewOrCreatedDetails, SuccessfulRedirectDetails],
                              putObjectRequest: PutObjectRequest)
                             (implicit pushOptions: PushOptions, logger: Logger) extends PushSuccessReport {
    def reportMessage =
      details.fold(
        newOrCreatedDetails => s"${newOrCreatedDetails.uploadType.pushAction.renderVerb} $s3Key ($reportDetails)",
        redirectDetails     => s"${redirectDetails.uploadType.pushAction.renderVerb} $s3Key to ${redirectDetails.redirectTarget}"
      )

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

    lazy val uploadSize = details.fold(
      newOrCreatedDetails => Some(newOrCreatedDetails.uploadSize),
      redirectDetails     => None
    )

    lazy val uploadSizeForHumans: Option[String] = uploadSize filter (_ => logger.verboseOutput) map humanReadableByteCount

    lazy val uploadSpeedForHumans: Option[String] =
      (for {
        dataSize <- uploadSize
        duration <- details.left.map(_.uploadDuration).left.toOption.flatten
      } yield {
        humanizeUploadSpeed(dataSize, duration)
      }) flatMap identity filter (_ => logger.verboseOutput)
  }
  
  object SuccessfulUpload {
    def humanizeUploadSpeed(uploadedBytes: Long, uploadDurations: UploadDuration*): Option[String] = {
      val totalDurationMillis = uploadDurations.foldLeft(0L){ (memo, duration) =>
        memo + duration
      }
      if (totalDurationMillis > 0) {
        val bytesPerMillisecond = uploadedBytes / totalDurationMillis
        val bytesPerSecond = bytesPerMillisecond * 1000 * uploadDurations.length
        Some(humanReadableByteCount(bytesPerSecond) + "/s")  
      } else {
        None
      }
    }
  }

  case class SuccessfulDelete(s3Key: S3Key)(implicit pushOptions: PushOptions) extends PushSuccessReport {
    def reportMessage = s"${Deleted.renderVerb} $s3Key"
  }

  case class FailedUpload(s3Key: S3Key, error: Throwable)(implicit logger: Logger) extends PushFailureReport {
    def reportMessage = errorMessage(s"Failed to upload $s3Key", error)
  }

  case class FailedDelete(s3Key: S3Key, error: Throwable)(implicit logger: Logger) extends PushFailureReport {
    def reportMessage = errorMessage(s"Failed to delete $s3Key", error)
  }

  type S3ClientProvider = (Config) => AmazonS3

  case class S3Setting(
    s3Client: S3ClientProvider = S3.awsS3Client,
    retryTimeUnit: TimeUnit = SECONDS
  ) extends RetrySetting
}
