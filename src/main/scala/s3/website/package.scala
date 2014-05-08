package s3

import scala.concurrent.{ExecutionContextExecutor, Future}
import s3.website.model.Error._
import s3.website.Logger._
import scala.concurrent.duration.{TimeUnit, Duration}
import s3.website.Utils._

package object website {
  trait Report {
    def reportMessage: String
  }
  trait SuccessReport extends Report

  trait FailureReport extends Report

  trait ErrorReport extends Report

  trait RetrySettings {
    def retryTimeUnit: TimeUnit
  }

  type Attempt = Int

  def retry[L <: Report, R](createFailureReport: (Throwable) => L, retryAction: (Attempt) => Future[Either[L, R]])
                           (implicit attempt: Attempt, retrySettings: RetrySettings, ec: ExecutionContextExecutor):
  PartialFunction[Throwable, Future[Either[L, R]]] = {
    case error: Throwable if attempt == 6 || isClientError(error) =>
      val failureReport = createFailureReport(error)
      fail(failureReport.reportMessage)
      Future(Left(failureReport))
    case error: Throwable =>
      val failureReport = createFailureReport(error)
      val sleepDuration = Duration(fibs.drop(attempt + 1).head, retrySettings.retryTimeUnit)
      pending(s"${failureReport.reportMessage}. Trying again in $sleepDuration.")
      Thread.sleep(sleepDuration.toMillis)
      retryAction(attempt + 1)
  }
}
