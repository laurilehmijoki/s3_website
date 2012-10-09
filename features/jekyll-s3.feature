Feature: jekyll-s3

  In order to push my jekyll site to s3
  As a blogger
  I want to run jekyll-s3 and say OMG it just worked!

  @s3
  Scenario: Push Jekyll site to S3
    When my Jekyll site is in "spec/test_site_dirs/my.blog.com"
    Then jekyll-s3 will push my blog to S3
