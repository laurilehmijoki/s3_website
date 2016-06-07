package s3.website

import s3.website.ErrorReport._
import s3.website.model.{FileUpdate, Config}
import com.amazonaws.services.cloudfront.{AmazonCloudFrontClient, AmazonCloudFront}
import com.amazonaws.services.cloudfront.model.{TooManyInvalidationsInProgressException, Paths, InvalidationBatch, CreateInvalidationRequest}
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import s3.website.S3.{SuccessfulDelete, PushSuccessReport, SuccessfulUpload}
import com.amazonaws.auth.BasicAWSCredentials
import java.net.{URLEncoder, URI}
import scala.concurrent.{ExecutionContextExecutor, Future}
import s3.website.model.Config.awsCredentials

object CloudFront {
  def invalidate(invalidationBatch: InvalidationBatch, distributionId: String, attempt: Attempt = 1)
                (implicit ec: ExecutionContextExecutor, cloudFrontSettings: CloudFrontSetting, config: Config, logger: Logger, pushOptions: PushOptions): InvalidationResult =
    Future {
      if (!pushOptions.dryRun) cloudFront createInvalidation new CreateInvalidationRequest(distributionId, invalidationBatch)
      val result = SuccessfulInvalidation(invalidationBatch.getPaths.getItems.size())
      logger.debug(invalidationBatch.getPaths.getItems.map(item => s"${Invalidated.renderVerb} $item") mkString "\n")
      logger.info(result)
      Right(result)
    } recoverWith (tooManyInvalidationsRetry(invalidationBatch, distributionId, attempt) orElse retry(attempt)(
      createFailureReport = error => FailedInvalidation(error),
      retryAction = nextAttempt => invalidate(invalidationBatch, distributionId, nextAttempt)
    ))

  def tooManyInvalidationsRetry(invalidationBatch: InvalidationBatch, distributionId: String, attempt: Attempt)
                               (implicit ec: ExecutionContextExecutor, logger: Logger, cloudFrontSettings: CloudFrontSetting, config: Config, pushOptions: PushOptions):
  PartialFunction[Throwable, InvalidationResult] = {
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
          |For more information, see http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html#InvalidationLimits"""
        .stripMargin
    if (attempt == 1)
      extendedInfo
    else
      basicInfo
  }

  def cloudFront(implicit config: Config, cloudFrontSettings: CloudFrontSetting) = cloudFrontSettings.cfClient(config)

  type InvalidationResult = Future[Either[FailedInvalidation, SuccessfulInvalidation]]

  type CloudFrontClientProvider = (Config) => AmazonCloudFront

  case class SuccessfulInvalidation(invalidatedItemsCount: Int)(implicit pushOptions: PushOptions) extends SuccessReport {
    def reportMessage = s"${Invalidated.renderVerb} ${invalidatedItemsCount ofType "item"} on CloudFront"
  }

  case class FailedInvalidation(error: Throwable)(implicit logger: Logger) extends ErrorReport {
    def reportMessage = errorMessage(s"Failed to invalidate the CloudFront distribution", error)
  }

  def awsCloudFrontClient(config: Config) = new AmazonCloudFrontClient(awsCredentials(config))

  def toInvalidationBatches(pushSuccessReports: Seq[PushSuccessReport])(implicit config: Config): Seq[InvalidationBatch] = {
    def callerReference = s"s3_website gem ${System.currentTimeMillis()}"
    if (config.cloudfront_wildcard_invalidation.contains(true) && pushSuccessReports.exists(needsInvalidation)) {
      return Seq(new InvalidationBatch withPaths(new Paths withItems "/*" withQuantity 1) withCallerReference callerReference)
    }
    def defaultPath(paths: Seq[String]): Option[String] = {
      // This is how we support the Default Root Object @ CloudFront (http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/DefaultRootObject.html)
      // We could do this more accurately by fetching the distribution config (http://docs.aws.amazon.com/AmazonCloudFront/latest/APIReference/GetConfig.html)
      // and reading the Default Root Object from there.
      val containsPotentialDefaultRootObject = paths
        .exists(
          _
            .replaceFirst("^/", "") // S3 keys do not begin with a slash
            .contains("/") == false // See if the S3 key is a top-level key (i.e., it is not within a directory)
        )
      if (containsPotentialDefaultRootObject) Some("/") else None
    }
    val indexPath = config.cloudfront_invalidate_root collect {
      case true if pushSuccessReports.nonEmpty => config.s3_key_prefix.map(prefix => s"/$prefix").getOrElse("") + "/index.html"
    }

    val invalidationPaths: Seq[String] = {
      val paths = pushSuccessReports
        .filter(needsInvalidation)
        .map(toInvalidationPath)
        .map(encodeUnsafeChars)
        .map(applyInvalidateRootSetting)

      val extraPathItems = defaultPath(paths) :: indexPath :: Nil collect {
        case Some(path) => path
      }

      paths ++ extraPathItems
    }

    invalidationPaths
      .grouped(1000) // CloudFront supports max 1000 invalidations in one request (http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html#InvalidationLimits)
      .map { batchKeys =>
        new InvalidationBatch() withPaths
          (new Paths() withItems batchKeys withQuantity batchKeys.size) withCallerReference callerReference
      }
      .toSeq
  }

  def applyInvalidateRootSetting(path: String)(implicit config: Config) =
    if (config.cloudfront_invalidate_root.contains(true))
      path.replaceFirst("/index.html$", "/")
    else
      path

  def toInvalidationPath(report: PushSuccessReport) = "/" + report.s3Key

  def encodeUnsafeChars(invalidationPath: String) =
    new URI("http", "cloudfront", invalidationPath, "")
      .toURL
      .getPath
      .replaceAll("'", URLEncoder.encode("'", "UTF-8")) // CloudFront does not accept ' in invalidation path
      .flatMap(chr => {
        if (("[^\\x00-\\x7F]".r findFirstIn chr.toString).isDefined)
          URLEncoder.encode(chr.toString, "UTF-8")
        else
          chr.toString
      })

  def needsInvalidation: PartialFunction[PushSuccessReport, Boolean] = {
    case succ: SuccessfulUpload => succ.details.fold(_.uploadType, _.uploadType) == FileUpdate
    case SuccessfulDelete(_) => true
    case _ => false
  }

  case class CloudFrontSetting(
    cfClient: CloudFrontClientProvider = CloudFront.awsCloudFrontClient,
    retryTimeUnit: TimeUnit = MINUTES
  ) extends RetrySetting
}
