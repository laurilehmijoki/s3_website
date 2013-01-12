Feature: remove a Jekyll blog post from S3

  In order to remove a blog post from S3
  As a blogger
  I want to run jekyll-s3 and see that my post was deleted from S3

  @one-file-to-delete
  Scenario: The user deletes a blog post
    When my Jekyll site is in "features/support/test_site_dirs/unpublish-a-post.com"
    Then jekyll-s3 will push my blog to S3
    And the output should equal
      """
      Deploying _site/* to jekyll-s3-test.net
      No new or changed files to upload
      Delete index.html: Success!
      Done! Go visit: http://jekyll-s3-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """
