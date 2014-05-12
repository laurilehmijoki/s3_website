package s3.website

import s3.website.model.{Update, Redirect, Config}
import com.amazonaws.services.cloudfront.{AmazonCloudFrontClient, AmazonCloudFront}
import s3.website.CloudFront.{CloudFrontSettings, SuccessfulInvalidation, FailedInvalidation}
import com.amazonaws.services.cloudfront.model.{TooManyInvalidationsInProgressException, Paths, InvalidationBatch, CreateInvalidationRequest}
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import s3.website.S3.{SuccessfulDelete, PushSuccessReport, SuccessfulUpload}
import com.amazonaws.auth.BasicAWSCredentials
import java.net.URI
import Utils._
import scala.concurrent.{ExecutionContextExecutor, Future}

class CloudFront(implicit cloudFrontSettings: CloudFrontSettings, config: Config, logger: Logger) {
  val cloudFront = cloudFrontSettings.cfClient(config)

  def invalidate(invalidationBatch: InvalidationBatch, distributionId: String, attempt: Attempt = 1)
                (implicit ec: ExecutionContextExecutor): InvalidationResult =
    Future {
      val invalidationReq = new CreateInvalidationRequest(distributionId, invalidationBatch)
      cloudFront.createInvalidation(invalidationReq)
      val result = SuccessfulInvalidation(invalidationBatch.getPaths.getItems.size())
      logger.info(result)
      Right(result)
    } recoverWith (tooManyInvalidationsRetry(invalidationBatch, distributionId, attempt) orElse retry(attempt)(
      createFailureReport = error => FailedInvalidation(error),
      retryAction = nextAttempt => invalidate(invalidationBatch, distributionId, nextAttempt)
    ))

  def tooManyInvalidationsRetry(invalidationBatch: InvalidationBatch, distributionId: String, attempt: Attempt)
                          (implicit ec: ExecutionContextExecutor, logger: Logger): PartialFunction[Throwable, InvalidationResult] = {
    case e: TooManyInvalidationsInProgressException =>
      val duration: Duration = Duration(
        (fibs drop attempt).head min 15, /* CloudFront invalidations complete within 15 minutes */
        cloudFrontSettings.retryTimeUnit
      )
      logger.pending(maxInvalidationsExceededInfo(duration, attempt))
      Thread.sleep(duration.toMillis)
      invalidate(invalidationBatch, distributionId, attempt + 1)
  }

  def maxInvalidationsExceededInfo(sleepDuration: Duration, attempt: Int) = {
    val basicInfo = s"The maximum amount of CloudFront invalidations has exceeded. Trying again in $sleepDuration, please wait."
    val extendedInfo =
      s"""|$basicInfo
          |  For more information, see http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html#InvalidationLimits"""
        .stripMargin
    if (attempt == 1)
      extendedInfo
    else
      basicInfo
  }

  type InvalidationResult = Future[Either[FailedInvalidation, SuccessfulInvalidation]]
}

object CloudFront {

  type CloudFrontClientProvider = (Config) => AmazonCloudFront

  case class SuccessfulInvalidation(invalidatedItemsCount: Int) extends SuccessReport {
    def reportMessage = s"Invalidated ${invalidatedItemsCount ofType "item"} on CloudFront"
  }

  case class FailedInvalidation(error: Throwable) extends FailureReport {
    def reportMessage = s"Failed to invalidate the CloudFront distribution (${error.getMessage})"
  }

  def awsCloudFrontClient(config: Config) =
    new AmazonCloudFrontClient(new BasicAWSCredentials(config.s3_id, config.s3_secret))
  
  def toInvalidationBatches(pushSuccessReports: Seq[PushSuccessReport])(implicit config: Config): Seq[InvalidationBatch] = {
    val invalidationPaths: Seq[String] = {
      def withDefaultPathIfNeeded(paths: Seq[String]) = {
        // This is how we support the Default Root Object @ CloudFront (http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/DefaultRootObject.html)
        // We do this more accurately by fetching the distribution config (http://docs.aws.amazon.com/AmazonCloudFront/latest/APIReference/GetConfig.html)
        // and reading the Default Root Object from there.
        val containsPotentialDefaultRootObject = paths
          .exists(
            _
              .replaceFirst("^/", "") // S3 keys do not begin with a slash
              .contains("/") == false // See if the S3 key is a top-level key (i.e., it is not within a directory)
          )
        if (containsPotentialDefaultRootObject) paths :+ "/" else paths
      }
      val paths = pushSuccessReports
        .filter(needsInvalidation) // Assume that redirect objects are never cached.
        .map(toInvalidationPath)
        .map (applyInvalidateRootSetting)
      withDefaultPathIfNeeded(paths)
    }

    invalidationPaths
      .grouped(1000) // CloudFront supports max 1000 invalidations in one request (http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html#InvalidationLimits)
      .map { batchKeys =>
        new InvalidationBatch() withPaths
          (new Paths() withItems batchKeys withQuantity batchKeys.size) withCallerReference
          s"s3_website gem ${System.currentTimeMillis()}"
      }
      .toSeq
  }

  def applyInvalidateRootSetting(path: String)(implicit config: Config) =
    if (config.cloudfront_invalidate_root.exists(_ == true))
      path.replaceFirst("/index.html$", "/")
    else
      path

  def toInvalidationPath(report: PushSuccessReport) = {
    def encodeUnsafeChars(path: String) =
      new URI(
        "http",
        "cloudfront", // We want to use the encoder in the URI class. These must be passed in.
        "/" + report.s3Key,  // CloudFront keys have the slash in front
        path
      ).toURL.getPath // The URL class encodes the unsafe characters
    val invalidationPath = "/" + report.s3Key  // CloudFront keys have the slash in front
    encodeUnsafeChars(invalidationPath)
  }


  def needsInvalidation: PartialFunction[PushSuccessReport, Boolean] = {
    case SuccessfulUpload(upload, _) => upload.uploadType match {
      case Update => true
      case _ => false
    }
    case SuccessfulDelete(_) => true
    case _ => false
  }

  case class CloudFrontSettings(
    cfClient: CloudFrontClientProvider = CloudFront.awsCloudFrontClient,
    retryTimeUnit: TimeUnit = MINUTES
  ) extends RetrySettings
}
