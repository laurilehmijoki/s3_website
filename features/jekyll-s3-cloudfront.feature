Feature: Invalidate the Cloudfront distribution

  In order to publish my posts
  As a blogger who delivers his blog via an S3-based Cloudfront distribution
  I want to run jekyll-s3
  And see, that the items in the distribution were invalidated
  So that my latest updates will be immediately available to readers

  @s3-and-cloudfront
  Scenario: Upload to S3 and then invalidate the Cloudfront distribution
    When my Jekyll site is in "features/support/test_site_dirs/cdn-powered.blog.fi"
    And the configuration contains the Cloudfront distribution id
    Then jekyll-s3 will push my blog to S3 and invalidate the Cloudfront distribution
    And report that it uploaded 2 new and 0 changed files into S3
