Feature: Security

  @empty-bucket
  Scenario: The user does not want to upload the s3_website.yml file
    When my S3 website is in "features/support/test_site_dirs/site-that-contains-s3-website-file.com"
    Then s3_website will push my blog to S3
    And the output should not contain "s3_website.yml"
    And the output should equal
      """
      Deploying features/support/test_site_dirs/site-that-contains-s3-website-file.com/_site/* to another-s3-website-test.net
      Calculating diff ... done
      No new or changed files to upload
      Done! Go visit: http://another-s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """
