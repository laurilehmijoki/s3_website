Feature: upload Jekyll site to S3

  In order to push my jekyll site to s3
  As a blogger
  I want to run jekyll-s3 and say OMG it just worked!

  @new-files
  Scenario: Push a new Jekyll site to S3
    When my Jekyll site is in "features/support/test_site_dirs/my.blog.com"
    Then jekyll-s3 will push my blog to S3
    And the output should contain
      """
      Deploying _site/* to jekyll-s3-test.net
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

  @new-files-for-sydney
  Scenario: Push a new Jekyll site to an S3 bucket in Sydney
    When my Jekyll site is in "features/support/test_site_dirs/my.sydney.blog.au"
    Then jekyll-s3 will push my blog to S3
    And the output should contain
      """
      Done! Go visit: http://jekyll-s3-test.net.s3-website-ap-southeast-2.amazonaws.com/index.html
      """

  @new-and-changed-files
  Scenario: Upload a new blog post and change an old post
    When my Jekyll site is in "features/support/test_site_dirs/new-and-changed-files.com"
    Then jekyll-s3 will push my blog to S3
    And the output should equal
      """
      Deploying _site/* to jekyll-s3-test.net
      Uploading 1 new and 1 changed file(s)
      Upload css/styles.css: Success!
      Upload index.html: Success!
      Done! Go visit: http://jekyll-s3-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @only-changed-files
  Scenario: Update an existing blog post
    When my Jekyll site is in "features/support/test_site_dirs/only-changed-files.com"
    Then jekyll-s3 will push my blog to S3
    And the output should equal
      """
      Deploying _site/* to jekyll-s3-test.net
      Uploading 1 changed file(s)
      Upload index.html: Success!
      Done! Go visit: http://jekyll-s3-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @no-new-or-changed-files
  Scenario: The user runs jekyll-s3 even though he doesn't have new or changed posts
    When my Jekyll site is in "features/support/test_site_dirs/no-new-or-changed-files.com"
    Then jekyll-s3 will push my blog to S3
    And the output should equal
      """
      Deploying _site/* to jekyll-s3-test.net
      No new or changed files to upload
      Done! Go visit: http://jekyll-s3-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """
