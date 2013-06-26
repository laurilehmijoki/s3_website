Feature: upload a Jekyll site

  @new-files
  Scenario: Push a Jekyll site to S3
    When my S3 website is in "features/support/test_site_dirs/jekyllrb.com"
    Then s3_website will push my blog to S3
    And the output should contain
      """
      Deploying features/support/test_site_dirs/jekyllrb.com/_site/* to s3-website-test.net
      Uploading 2 new file(s)
      """
    And the output should contain
      """
      Upload css/styles.css: Success!
      """
    And the output should contain
      """
      Upload index.html: Success!
      """
