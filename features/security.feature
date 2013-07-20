Feature: Security

  @new-files
  @wip
  Scenario: The user does not want the s3_website.yml file to be uploaded
    When my S3 website is in "features/support/test_site_dirs/site-that-contains-s3-website-file.com"
    Then s3_website will push my blog to S3
    And the output should not contain "s3_website.yml"
