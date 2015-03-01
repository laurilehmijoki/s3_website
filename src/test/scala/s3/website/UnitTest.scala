package s3.website

import org.specs2.mutable.Specification

class UnitTest extends Specification {
  "rubyRegexMatches" should {
    "accept valid URL characters" in {
      Ruby.rubyRegexMatches("arnold's file.txt", ".txt") must beTrue
    }
  }
}
