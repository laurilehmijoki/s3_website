package s3.website

import com.amazonaws.http.AmazonHttpClient
import org.apache.commons.logging.LogFactory
import org.specs2.mutable.Specification

class AwsSdkSpec extends Specification {

  "AWS SDK" should {
    "not log INFO level messages" in {
      // See https://github.com/laurilehmijoki/s3_website/issues/104 for discussion
      LogFactory.getLog(classOf[AmazonHttpClient]).isInfoEnabled must beFalse
    }
  }
}
