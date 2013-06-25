Feature: upload S3 website to S3

  In order to push my website to S3
  As a blogger
  I want to run s3_website and say OMG it just worked!

  @new-files
  Scenario: Push a new S3 website to S3
    When my S3 website is in "features/support/test_site_dirs/my.blog.com"
    Then s3_website will push my blog to S3
    And the output should contain
      """
      Deploying features/support/test_site_dirs/my.blog.com/_site/* to s3-website-test.net
      Uploading 2 new file(s)
      Upload css/styles.css: Success!
      Upload index.html: Success!
      """

  @new-files-for-sydney
  Scenario: Push a new S3 website to an S3 bucket in Sydney
    When my S3 website is in "features/support/test_site_dirs/my.sydney.blog.au"
    Then s3_website will push my blog to S3
    And the output should contain
      """
      Done! Go visit: http://s3-website-test.net.s3-website-ap-southeast-2.amazonaws.com/index.html
      """

  @new-and-changed-files
  Scenario: Upload a new blog post and change an old post
    When my S3 website is in "features/support/test_site_dirs/new-and-changed-files.com"
    Then s3_website will push my blog to S3
    And the output should contain
      """
      Deploying features/support/test_site_dirs/new-and-changed-files.com/_site/* to s3-website-test.net
      Uploading 1 new and 1 changed file(s)
      """
    And the output should contain
      """
      Upload css/styles.css: Success!
      """
    And the output should contain
      """
      Upload index.html: Success!
      """
    And the output should contain
      """
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @only-changed-files
  Scenario: Update an existing blog post
    When my S3 website is in "features/support/test_site_dirs/only-changed-files.com"
    Then s3_website will push my blog to S3
    And the output should equal
      """
      Deploying features/support/test_site_dirs/only-changed-files.com/_site/* to s3-website-test.net
      Uploading 1 changed file(s)
      Upload index.html: Success!
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @no-new-or-changed-files
  Scenario: The user runs s3_website even though he doesn't have new or changed posts
    When my S3 website is in "features/support/test_site_dirs/no-new-or-changed-files.com"
    Then s3_website will push my blog to S3
    And the output should equal
      """
      Deploying features/support/test_site_dirs/no-new-or-changed-files.com/_site/* to s3-website-test.net
      No new or changed files to upload
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """
