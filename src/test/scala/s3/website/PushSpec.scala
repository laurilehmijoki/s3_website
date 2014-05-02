package s3.website

import org.specs2.mutable.Specification
import s3.website.Push.PushCounts
import s3.website.model.UserError
import scala.collection.parallel.ParSeq

class PushSpec extends Specification {

  "#resolvePushCounts" should {
    "count errors as failures" in {
      val finishedUploads = ParSeq(Left(UserError("fail")))
      Push.resolvePushCounts(finishedUploads) must equalTo(PushCounts(failures = 1))
    }
  }
}
