Feature: remove an S3 website page from S3

  In order to remove a webpage from S3
  As a blogger
  I want to run s3_website and see that my webpage was deleted from S3

  @one-file-to-delete
  Scenario: The user deletes a blog post
    When my S3 website is in "features/support/test_site_dirs/unpublish-a-post.com"
    And I call the push command
    Then the output should equal
      """
      Deploying features/support/test_site_dirs/unpublish-a-post.com/_site/* to s3-website-test.net
      Calculating diff ... done
      No new or changed files to upload
      Delete index.html: Success!
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """
