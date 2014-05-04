package s3.website.model

trait Error {
  def message: String
}

case class UserError(message: String) extends Error

case class IOError(exception: Throwable) extends Error {
  def message = exception.getMessage
}