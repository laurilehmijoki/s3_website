Feature: upload a Nanoc site

  @new-files
  Scenario: Push a Nanoc site to S3
    When my S3 website is in "features/support/test_site_dirs/nanoc.ws"
    Then s3_website will push my blog to S3
    And the output should contain
      """
      Deploying features/support/test_site_dirs/nanoc.ws/public/output/* to s3-website-test.net
      Uploading 2 new file(s)
      Upload css/styles.css: Success!
      Upload index.html: Success!
      """
