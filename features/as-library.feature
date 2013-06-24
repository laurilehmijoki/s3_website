Feature: Using s3_website as a library

  As a developer
  I want to programmatically use the s3_website API
  So that I can extend my software with the capabilities of s3_website

  @one-file-to-delete
  Scenario: Developer wants feedback on how many files s3_website deleted
    When my S3 website is in "features/support/test_site_dirs/unpublish-a-post.com"
    Then s3_website will push my blog to S3
    And report that it deleted 1 file from S3

  @new-and-changed-files
  Scenario: Developer wants feedback on how many files s3_website uploaded
    When my S3 website is in "features/support/test_site_dirs/new-and-changed-files.com"
    Then s3_website will push my blog to S3
    And report that it uploaded 1 new and 1 changed files into S3

  @s3-and-cloudfront-when-updating-a-file
  Scenario: Developer wants feedback on how many Cloudfront items s3_website invalidated
    When my S3 website is in "features/support/test_site_dirs/cdn-powered.with-one-change.blog.fi"
    Then s3_website will push my blog to S3 and invalidate the Cloudfront distribution
    And report that it invalidated 2 Cloudfront item

  @create-redirect
  Scenario: Developer wants feedback on how many redirects s3_website created
    When my S3 website is in "features/support/test_site_dirs/create-redirects"
    Then s3_website will push my blog to S3
    And report that it created 2 new redirects
