package s3

import s3.website.Ruby._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.{TimeUnit, Duration}
import s3.website.S3.{PushSuccessReport, PushFailureReport}
import com.amazonaws.AmazonServiceException
import s3.website.model.{Config, Site}
import java.io.File

import scala.util.matching.Regex

package object website {
  trait Report {
    def reportMessage: String
  }
  trait SuccessReport extends Report

  trait ErrorReport extends Report

  object ErrorReport {
    def apply(t: Throwable)(implicit logger: Logger) = new ErrorReport {
      override def reportMessage = errorMessage(t)
    }

    def apply(msg: String) = new ErrorReport {
      override def reportMessage = msg
    }

    def errorMessage(msg: String, t: Throwable)(implicit logger: Logger): String = s"$msg (${errorMessage(t)})"

    def errorMessage(t: Throwable)(implicit logger: Logger): String =  {
      val extendedReport =
        if (logger.verboseOutput)
          Some(t.getStackTrace)
        else
          None
      s"${t.getMessage}${extendedReport.fold("")(stackTraceElems => "\n" + stackTraceElems.mkString("\n"))}"
    }
  }

  trait RetrySetting {
    def retryTimeUnit: TimeUnit
  }
  
  trait PushOptions {
    /**
     * @return true if the CLI option --dry-run is on
     */
    def dryRun: Boolean

    /**
     * @return true if the CLI option --force is on
     */
    def force: Boolean
  }

  case class S3KeyRegex(keyRegex: Regex) {
    def matches(s3Key: S3Key) = rubyRegexMatches(s3Key.key, keyRegex.pattern.pattern())
  }

  trait S3Key {
    val key: String
    override def toString = key
  }

  object S3Key {
    def prefix(s3_key_prefix: Option[String]) = s3_key_prefix.map(prefix => if (prefix.endsWith("/")) prefix else prefix + "/").getOrElse("")

    def isIgnoredBecauseOfPrefix(s3Key: S3Key)(implicit site: Site) = s3Key.key.startsWith(prefix(site.config.s3_key_prefix))

    case class S3KeyClass(key: String) extends S3Key
    def build(key: String, s3_key_prefix: Option[String]): S3Key = S3KeyClass(prefix(s3_key_prefix) + key)
  }
  
  case class S3KeyGlob[T](globs: Map[String, T]) {
    def globMatch(s3Key: S3Key): Option[T] = {
      def respectMostSpecific(globs: Map[String, T]) = globs.toSeq.sortBy(_._1.length).reverse
      val matcher = (glob: String, value: T) =>
        rubyRuntime.evalScriptlet(
          s"""|# encoding: utf-8
             |File.fnmatch('$glob', "$s3Key")""".stripMargin)
          .toJava(classOf[Boolean])
          .asInstanceOf[Boolean]
      val fileGlobMatch = respectMostSpecific(globs) find Function.tupled(matcher)
      fileGlobMatch map (_._2)
    }
  }
  
  case class S3KeyRegexes(s3KeyRegexes: Seq[S3KeyRegex]) {
    def matches(s3Key: S3Key) = s3KeyRegexes exists (
      (keyRegex: S3KeyRegex) => keyRegex matches s3Key
    )
  }

  type UploadDuration = Long

  trait PushAction {
    def actionName = getClass.getSimpleName.replace("$", "") // case object class names contain the '$' char

    def renderVerb(implicit pushOptions: PushOptions): String =
      if (pushOptions.dryRun)
        s"Would have ${actionName.toLowerCase}"
      else
        s"$actionName"
  }
  case object Created     extends PushAction
  case object Updated     extends PushAction
  case object Redirected  extends PushAction
  case object Deleted     extends PushAction
  case object Transferred extends PushAction
  case object Invalidated extends PushAction
  case object Applied     extends PushAction
  case object PushNothing extends PushAction {
    override def renderVerb(implicit pushOptions: PushOptions) =
      if (pushOptions.dryRun)
        s"Would have pushed nothing"
      else
        s"There was nothing to push"
  }
  case object Deploy      extends PushAction {
    override def renderVerb(implicit pushOptions: PushOptions) =
      if (pushOptions.dryRun)
        s"Simulating the deployment of"
      else
        s"Deploying"
  }

  type PushErrorOrSuccess = Either[PushFailureReport, PushSuccessReport]

  type Attempt = Int

  type MD5 = String

  def retry[L <: Report, R](attempt: Attempt)
                           (createFailureReport: (Throwable) => L, retryAction: (Attempt) => Future[Either[L, R]])
                           (implicit retrySetting: RetrySetting, ec: ExecutionContextExecutor, logger: Logger):
  PartialFunction[Throwable, Future[Either[L, R]]] = {
    case error: Throwable if attempt == 6 || isIrrecoverable(error) =>
      val failureReport = createFailureReport(error)
      logger.fail(failureReport.reportMessage)
      Future(Left(failureReport))
    case error: Throwable =>
      val failureReport = createFailureReport(error)
      val sleepDuration = Duration(fibs.drop(attempt + 1).head, retrySetting.retryTimeUnit)
      logger.pending(s"${failureReport.reportMessage}. Trying again in $sleepDuration.")
      Thread.sleep(sleepDuration.toMillis)
      retryAction(attempt + 1)
  }

  def isIrrecoverable(error: Throwable) = {
    val httpStatusCode =
      error match {
        case exception: AmazonServiceException => Some(exception.getStatusCode)
        case _ => None
      }
    val isAwsTimeoutException =
      error match {
        case exception: AmazonServiceException =>
          // See http://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html#ErrorCodeList
          exception.getErrorCode == "RequestTimeout"
        case _ => false
      }
    httpStatusCode.exists(c => c >= 400 && c < 500) && !isAwsTimeoutException
  }
  
  implicit class NumReport(val num: Int) extends AnyVal {
    def ofType(itemType: String) = countToString(num, itemType)

    private def countToString(count: Int, singular: String) = {
      def plural = s"${singular}s"
      s"$count ${if (count > 1) plural else singular}"
    }
  }

  implicit def site2Config(implicit site: Site): Config = site.config

  type ErrorOrFile = Either[ErrorReport, File]

  lazy val fibs: Stream[Int] = 0 #:: 1 #:: fibs.zip(fibs.tail).map { n => n._1 + n._2 }
}
