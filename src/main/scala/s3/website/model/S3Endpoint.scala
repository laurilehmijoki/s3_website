package s3.website.model

import s3.website.ErrorReport

case class S3Endpoint(
  s3WebsiteHostname: String,
  s3Hostname: String
)

object S3Endpoint {
  val defaultEndpoint = S3Endpoint("s3-website-us-east-1.amazonaws.com", "s3.amazonaws.com")

  def forString(locationConstraint: String): Either[ErrorReport, S3Endpoint] = locationConstraint match {
    case "EU" | "eu-west-1" => Right(S3Endpoint("s3-website-eu-west-1.amazonaws.com",      "s3-eu-west-1.amazonaws.com"))
    case "us-east-1" =>        Right(defaultEndpoint)
    case "us-west-1" =>        Right(S3Endpoint("s3-website-us-west-1.amazonaws.com",      "s3-us-west-1.amazonaws.com"))
    case "us-west-2" =>        Right(S3Endpoint("s3-website-us-west-2.amazonaws.com",      "s3-us-west-2.amazonaws.com"))
    case "ap-southeast-1" =>   Right(S3Endpoint("s3-website-ap-southeast-1.amazonaws.com", "s3-ap-southeast-1.amazonaws.com"))
    case "ap-southeast-2" =>   Right(S3Endpoint("s3-website-ap-southeast-2.amazonaws.com", "s3-ap-southeast-2.amazonaws.com"))
    case "ap-northeast-1" =>   Right(S3Endpoint("s3-website-ap-northeast-1.amazonaws.com", "s3-ap-northeast-1.amazonaws.com"))
    case "sa-east-1" =>        Right(S3Endpoint("s3-website-sa-east-1.amazonaws.com",      "s3-sa-east-1.amazonaws.com"))
    case _ =>                  Left(ErrorReport(s"Unrecognised endpoint: $locationConstraint"))
  }
}