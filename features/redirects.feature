Feature: configure redirects

  @create-redirect
  Scenario: The user wants to configure new redirects for HTTP resources
    When my S3 website is in "features/support/test_site_dirs/create-redirects"
    Then s3_website will push my blog to S3
    And the output should equal
      """
      Deploying _site/* to s3-website-test.net
      No new or changed files to upload
      Creating new redirects ...
        Redirect welcome.php to /welcome: Success!
        Redirect pets/dogs to /cats-and-dogs/wuf: Success!
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """
