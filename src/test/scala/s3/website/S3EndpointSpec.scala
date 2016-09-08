package s3.website

import org.specs2.mutable.Specification
import s3.website.model.S3Endpoint

class S3EndpointSpec extends Specification {

  "S3Endpoint" should {
    "map EU to eu-west-1" in {
      S3Endpoint.fromString("EU").s3WebsiteHostname must equalTo("s3-website-eu-west-1.amazonaws.com")
    }
  }

}

