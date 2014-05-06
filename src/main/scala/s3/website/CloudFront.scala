package s3.website

import s3.website.model.{Redirect, Config}
import com.amazonaws.services.cloudfront.{AmazonCloudFrontClient, AmazonCloudFront}
import s3.website.CloudFront.{FailedInvalidation, SuccessfulInvalidation, CloudFrontClientProvider}
import scala.util.{Failure, Success, Try}
import com.amazonaws.services.cloudfront.model.{TooManyInvalidationsInProgressException, Paths, InvalidationBatch, CreateInvalidationRequest}
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import s3.website.S3.{SuccessfulUpload, PushSuccessReport}
import com.amazonaws.auth.BasicAWSCredentials
import s3.website.Logger._
import s3.website.S3.SuccessfulUpload
import scala.util.Failure
import s3.website.CloudFront.SuccessfulInvalidation
import scala.util.Success
import s3.website.CloudFront.FailedInvalidation

class CloudFront(implicit cfClient: CloudFrontClientProvider, sleepUnit: TimeUnit) {

  def invalidate(invalidationBatch: InvalidationBatch, distributionId: String)(implicit config: Config): InvalidationResult = {
    def tryInvalidate(implicit attempt: Int = 1): Try[SuccessfulInvalidation] =
      Try {
        val invalidationReq = new CreateInvalidationRequest(distributionId, invalidationBatch)
        cfClient(config).createInvalidation(invalidationReq)
        val result = SuccessfulInvalidation(invalidationBatch.getPaths.getItems.size())
        info(result)
        result
      } recoverWith {
        case e: TooManyInvalidationsInProgressException =>
          implicit val duration: Duration = Duration(
            (fibs drop attempt).head min 15, /* AWS docs way that invalidations complete in 15 minutes */
            sleepUnit
          )
          pending(maxInvalidationsExceededInfo)
          Thread.sleep(duration.toMillis)
          tryInvalidate(attempt + 1)
      }

    tryInvalidate() match {
      case Success(res) =>
        Right(res)
      case Failure(err) =>
        val report = FailedInvalidation(err)
        info(report)
        Left(report)
    }
  }

  def maxInvalidationsExceededInfo(implicit sleepDuration: Duration, attempt: Int) = {
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

  type InvalidationResult = Either[FailedInvalidation, SuccessfulInvalidation]

  lazy val fibs: Stream[Int] = 0 #:: 1 #:: fibs.zip(fibs.tail).map { n => n._1 + n._2 }
}

object CloudFront {

  type CloudFrontClientProvider = (Config) => AmazonCloudFront

  case class SuccessfulInvalidation(invalidatedItemsCount: Int) extends SuccessReport {
    def reportMessage = s"Invalidated $invalidatedItemsCount item(s) on CloudFront"
  }

  case class FailedInvalidation(error: Throwable) extends FailureReport{
    def reportMessage = s"Failed to invalidate the CloudFront distribution (${error.getMessage})"
  }

  def awsCloudFrontClient(config: Config) =
    new AmazonCloudFrontClient(new BasicAWSCredentials(config.s3_id, config.s3_secret))
  
  def toInvalidationBatches(pushSuccessReports: Seq[PushSuccessReport])(implicit config: Config): Seq[InvalidationBatch] =
    pushSuccessReports
      .filterNot(isRedirect) // Assume that redirect objects are never cached.
      .map("/" + _.s3Key) // CloudFront keys always have the slash in front
      .map { path =>
        if (config.cloudfront_invalidate_root.exists(_ == true))
          path.replaceFirst("/index.html$", "/")
        else
          path
      }
      .grouped(1000) // CloudFront supports max 1000 invalidations in one request (http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html#InvalidationLimits)
      .map { batchKeys =>
        new InvalidationBatch() withPaths
          (new Paths() withItems batchKeys withQuantity batchKeys.size) withCallerReference
            s"s3_website gem ${System.currentTimeMillis()}"
      }
      .toSeq

  def isRedirect: PartialFunction[PushSuccessReport, Boolean] = {
    case SuccessfulUpload(upload) => upload.uploadType match {
      case Redirect => true
      case _ => false
    }
    case _ => false
  }
}
