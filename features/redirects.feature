Feature: configure redirects

  @create-redirect
  Scenario: The user wants to configure new redirects for HTTP resources
    When my S3 website is in "features/support/test_site_dirs/create-redirects"
    And I call the push command
    Then the output should contain
      """
      Creating new redirects ...
        Redirect welcome.php to /welcome: Success!
        Redirect pets/dogs to /cats-and-dogs/wuf: Success!
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """
