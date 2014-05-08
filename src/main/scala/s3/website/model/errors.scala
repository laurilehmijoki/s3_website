package s3.website.model

import s3.website.ErrorReport

case class ClientError(reportMessage: String) extends ErrorReport

case class IOError(exception: Throwable) extends ErrorReport {
  def reportMessage = exception.getMessage
}