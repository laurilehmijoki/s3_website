Feature: Invalidate the Cloudfront distribution

  In order to publish my posts
  As a blogger who delivers his blog via an S3-based Cloudfront distribution
  I want to run jekyll-s3
  And see, that the items in the distribution were invalidated
  So that my latest updates will be immediately available to readers

  Scenario: Run jekyll-s3 for the first time
    Given a directory named "_site"
    When I run `jekyll-s3`
    Then the output should contain:
      """
      I've just generated a file called _jekyll_s3.yml. Go put your details in it!
      """
    Then the file "_jekyll_s3.yml" should contain:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      cloudfront_distribution_id: YOUR_CLOUDFRONT_DIST_ID (OPTIONAL)
      """

  @s3-and-cloudfront
  Scenario: Upload to S3 and then invalidate the Cloudfront distribution
    When my Jekyll site is in "features/support/test_site_dirs/cdn-powered.blog.fi"
    And the configuration contains the Cloudfront distribution id
    Then jekyll-s3 will push my blog to S3 and invalidate the Cloudfront distribution
    And report that it uploaded 2 files into S3
