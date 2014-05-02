Feature: upload S3 website to S3

  In order to push my website to S3
  As a blogger
  I want to run s3_website and say OMG it just worked!

  @new-files
  Scenario: Push a new S3 website to S3
    When my S3 website is in "features/support/test_site_dirs/my.blog.com"
    And I call the push command
    Then the output should contain
      """
      Deploying features/support/test_site_dirs/my.blog.com/_site/* to s3-website-test.net
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

  @new-files
  @network-io
  @starts-new-os-process
  Scenario: The website resides in a non-standard directory
    Given a directory named "this-is-a-non-standard-website-dir"
    And a file named "s3_website.yml" with:
      """
      s3_id: id
      s3_secret: secret
      s3_bucket: website.net
      """
    And I run `s3_website push --site this-is-a-non-standard-website-dir`
    Then the output should contain:
      """
      Deploying this-is-a-non-standard-website-dir/* to website.net
      """

  @new-files-for-sydney
  Scenario: Push a new S3 website to an S3 bucket in Sydney
    When my S3 website is in "features/support/test_site_dirs/my.sydney.blog.au"
    And I call the push command
    Then the output should contain
      """
      Done! Go visit: http://s3-website-test-sydney.net.s3-website-ap-southeast-2.amazonaws.com/index.html
      """

  @new-and-changed-files
  Scenario: Upload a new blog post and change an old post
    When my S3 website is in "features/support/test_site_dirs/new-and-changed-files.com"
    And I call the push command
    Then the output should contain
      """
      Deploying features/support/test_site_dirs/new-and-changed-files.com/_site/* to s3-website-test.net
      Downloading list of the objects in a bucket ... done
      Calculating diff ... done
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
    And I call the push command
    Then the output should equal
      """
      Deploying features/support/test_site_dirs/only-changed-files.com/_site/* to s3-website-test.net
      Downloading list of the objects in a bucket ... done
      Calculating diff ... done
      Uploading 1 changed file(s)
      Upload index.html: Success!
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @no-new-or-changed-files
  Scenario: The user runs s3_website even though he doesn't have new or changed posts
    When my S3 website is in "features/support/test_site_dirs/no-new-or-changed-files.com"
    And I call the push command
    Then the output should equal
      """
      Deploying features/support/test_site_dirs/no-new-or-changed-files.com/_site/* to s3-website-test.net
      Downloading list of the objects in a bucket ... done
      Calculating diff ... done
      No new or changed files to upload
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @new-and-changed-files
  Scenario: The blogger user does not want to upload certain files
    When my S3 website is in "features/support/test_site_dirs/ignored-files.com"
    And I call the push command
    Then the output should equal
      """
      Deploying features/support/test_site_dirs/ignored-files.com/_site/* to s3-website-test.net
      Downloading list of the objects in a bucket ... done
      Calculating diff ... done
      Uploading 1 changed file(s)
      Upload css/styles.css: Success!
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @changed-files-large-site
  Scenario: The maintainer of a large website wants to update, add and remove only a few files
    When my S3 website is in "features/support/test_site_dirs/large-site-changed.com"
    And I call the push command
    Then the output should contain
      """
      Deploying features/support/test_site_dirs/large-site-changed.com/_site/* to s3-website-test.net
      Downloading list of the objects in a bucket ... done
      Calculating diff ... done
      Uploading 1 new and 3 changed file(s)
      """
    And the output should contain
      """
      Upload page0.html: Success!
      """
    And the output should contain
      """
      Upload style0.css: Success!
      """
    And the output should contain
      """
      Upload data0.txt: Success!
      """
    And the output should contain
      """
      Upload new_page999.html: Success!
      """
    And the output should contain
      """
      Delete page999.html: Success!
      """
    And the output should contain
      """
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

