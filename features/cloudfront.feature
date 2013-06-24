Feature: Invalidate the Cloudfront distribution

  In order to publish my posts
  As a blogger who delivers his blog via an S3-based Cloudfront distribution
  I want to run s3_website
  And see, that the items in the distribution were invalidated
  So that my latest updates will be immediately available to readers

  @s3-and-cloudfront
  Scenario: Upload to S3 and then invalidate the Cloudfront distribution
    When my S3 website is in "features/support/test_site_dirs/cdn-powered.blog.fi"
    Then s3_website will push my blog to S3 and invalidate the Cloudfront distribution
    And the output should contain
      """
      Invalidating Cloudfront items...
        /
      succeeded
      """

  @s3-and-cloudfront-when-updating-a-file
  Scenario: Update a blog entry and then upload
    When my S3 website is in "features/support/test_site_dirs/cdn-powered.with-one-change.blog.fi"
    Then s3_website will push my blog to S3 and invalidate the Cloudfront distribution
    And the output should equal
      """
      Deploying _site/* to s3-website-test.net
      Uploading 1 changed file(s)
      Upload index.html: Success!
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html
      Invalidating Cloudfront items...
        /index.html
        /
      succeeded

      """
