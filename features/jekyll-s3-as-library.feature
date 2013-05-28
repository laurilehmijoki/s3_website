Feature: Using Jekyll-s3 as a library

  As a developer
  I want to programmatically use the Jekyll-s3 API
  So that I can extend my software with the capabilities of Jekyll-s3

  @one-file-to-delete
  Scenario: Developer wants feedback on how many files Jekyll-s3 deleted
    When my Jekyll site is in "features/support/test_site_dirs/unpublish-a-post.com"
    Then jekyll-s3 will push my blog to S3
    And report that it deleted 1 file from S3

  @new-and-changed-files
  Scenario: Developer wants feedback on how many files Jekyll-s3 uploaded
    When my Jekyll site is in "features/support/test_site_dirs/new-and-changed-files.com"
    Then jekyll-s3 will push my blog to S3
    And report that it uploaded 1 new and 1 changed files into S3

  @s3-and-cloudfront-when-updating-a-file
  Scenario: Developer wants feedback on how many Cloudfront items Jekyll-s3 invalidated
    When my Jekyll site is in "features/support/test_site_dirs/cdn-powered.with-one-change.blog.fi"
    Then jekyll-s3 will push my blog to S3 and invalidate the Cloudfront distribution
    And report that it invalidated 2 Cloudfront item

  @create-redirect
  Scenario: Developer wants feedback on how many redirects Jekyll-s3 created
    When my Jekyll site is in "features/support/test_site_dirs/create-redirects"
    Then jekyll-s3 will push my blog to S3
    And report that it created 2 new redirects
