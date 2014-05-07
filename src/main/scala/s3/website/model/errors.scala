package s3.website.model

import com.amazonaws.AmazonServiceException
import com.amazonaws.AmazonServiceException.ErrorType
import com.amazonaws.AmazonServiceException.ErrorType.Client

trait Error {
  def message: String
}

object Error {
  def isClientError(error: Throwable) =
    error.isInstanceOf[AmazonServiceException] && error.asInstanceOf[AmazonServiceException].getErrorType == Client
}

case class UserError(message: String) extends Error

case class IOError(exception: Throwable) extends Error {
  def message = exception.getMessage
}