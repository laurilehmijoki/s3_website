package s3.website.model

import com.amazonaws.AmazonServiceException
import com.amazonaws.AmazonServiceException.ErrorType.Client
import s3.website.ErrorReport

object Error {
  def isClientError(error: Throwable) =
    error.isInstanceOf[AmazonServiceException] && error.asInstanceOf[AmazonServiceException].getErrorType == Client
}

case class UserError(reportMessage: String) extends ErrorReport

case class IOError(exception: Throwable) extends ErrorReport {
  def reportMessage = exception.getMessage
}