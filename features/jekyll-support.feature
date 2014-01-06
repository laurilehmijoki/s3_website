Feature: upload a Jekyll site

  @new-files
  Scenario: Push a Jekyll site to S3
    When my S3 website is in "features/support/test_site_dirs/jekyllrb.com"
    And I call the push command
    Then the output should contain
      """
      Deploying features/support/test_site_dirs/jekyllrb.com/_site/* to s3-website-test.net
      Calculating diff ... done
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
