Feature: upload a Nanoc site

  @new-files
  Scenario: Push a Nanoc site to S3
    When my S3 website is in "features/support/test_site_dirs/nanoc.ws"
    And I call the push command
    Then the output should contain
      """
      Deploying features/support/test_site_dirs/nanoc.ws/public/output/* to s3-website-test.net
      Downloading list of the objects in a bucket ... done
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
