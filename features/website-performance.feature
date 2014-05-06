Feature: improve response times of your S3 website website

  As a blogger
  I want to benefit from HTTP performance optimisations
  So that my readers would not have to wait long for my website to load

  @new-files
  Scenario: Set Cache-Control: max-age for all uploaded files
    When my S3 website is in "features/support/test_site_dirs/site.with.maxage.com"
    And I call the push command
    Then the output should contain
      """
      Upload css/styles.css [max-age=120]: Success!
      """
    And the output should contain
      """
      Upload index.html [max-age=120]: Success!
      """

  @new-files
  Scenario: Set Cache-Control: max-age for CSS files only
    When my S3 website is in "features/support/test_site_dirs/site.with.css-maxage.com"
    And I call the push command
    Then the output should contain
      """
      Upload css/styles.css [max-age=100]: Success!
      """
    And the output should contain
      """
      Upload index.html [max-age=0]: Success!
      """

  @new-files
  Scenario: Set Content-Encoding: gzip HTTP header for HTML files
    When my S3 website is in "features/support/test_site_dirs/site.with.gzipped-html.com"
    And I call the push command
    Then the output should contain
      """
      Upload css/styles.css: Success!
      """
    And the output should contain
      """
      Upload index.html [gzipped]: Success!
      """

  @new-files
  Scenario: Set both the Content-Encoding: gzip and Cache-Control: max-age headers
    When my S3 website is in "features/support/test_site_dirs/site.with.gzipped-and-max-aged-content.com"
    And I call the push command
    Then the output should contain
      """
      Upload css/styles.css [gzipped] [max-age=300]: Success!
      """
    And the output should contain
      """
      Upload index.html [gzipped] [max-age=300]: Success!
      """

  @no-new-or-changed-files-gzipped-content
  Scenario: Try to upload unchanged files with gzip enabled and make sure that the diff is calculated correctly
    When my S3 website is in "features/support/test_site_dirs/site.with.gzipped-and-max-aged-content.com"
    And I call the push command
    Then the output should equal
      """
      Deploying features/support/test_site_dirs/site.with.gzipped-and-max-aged-content.com/_site/* to s3-website-test.net
      Downloading list of the objects in a bucket ... done
      Calculating diff ... done
      No new or changed files to upload
      Done! Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @changed-files-after-gzip-config-update
  Scenario: Upload a blog with unchanged content but gzipping disabled (while S3 objects are gzipped)
    When my S3 website is in "features/support/test_site_dirs/my.blog.com"
    And I call the push command
    Then the output should contain
      """
      Deploying features/support/test_site_dirs/my.blog.com/_site/* to s3-website-test.net
      Downloading list of the objects in a bucket ... done
      Calculating diff ... done
      Uploading 2 changed file(s)
      """
    And the output should contain
      """
      Upload css/styles.css: Success!
      """
    And the output should contain
      """
      Upload index.html: Success!
      """