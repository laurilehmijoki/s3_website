Feature: Instructions for a new user

  As a new s3_website user
  I would like to get helpful feedback when running `s3_website`
  So that I can upload my S3 website to S3 without headache

  @starts-new-os-process
  Scenario: Run s3_website in the wrong directory
    When I run `s3_website push`
    Then the output should contain:
      """
      I can't find a website in any of the following directories: public/output, _site. Please specify the location of the website with the --site option.
      """

  @starts-new-os-process
  Scenario: Configuration is incomplete
    When I run `s3_website push`
    Then the exit status should be 1

  @starts-new-os-process
  Scenario: Create placeholder config file
    Given a directory named "_site"
    When I run `s3_website cfg create`
    Then the output should contain:
      """
      I've just generated a file called s3_website.yml. Go put your details in it!
      """
    Then the file "s3_website.yml" should contain:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      """

  @starts-new-os-process
  Scenario: Run s3_website push for the first time
    Given a directory named "_site"
    When I run `s3_website push`
    Then the output should contain:
      """
      I've just generated a file called s3_website.yml. Go put your details in it!
      """
    Then the file "s3_website.yml" should contain:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      """

  @starts-new-os-process
  Scenario: Run s3_website with an empty configuration file
    Given a directory named "_site"
    And an empty file named "s3_website.yml"
    When I run `s3_website push`
    Then the output should contain:
      """
      I can't parse the file s3_website.yml. It should look like this:
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      """

  @starts-new-os-process
  Scenario: The user wants to know the available configurations by looking at the cfg file
    Given a directory named "_site"
    When I run `s3_website push`
    Then the file "s3_website.yml" should contain:
      """
      # max_age:
      """
    Then the file "s3_website.yml" should contain:
      """
      # gzip:
      """
    Then the file "s3_website.yml" should contain:
      """
      # s3_endpoint:
      """
    Then the file "s3_website.yml" should contain:
      """
      # ignore_on_server:
      """
    Then the file "s3_website.yml" should contain:
      """
      # s3_reduced_redundancy:
      """
    Then the file "s3_website.yml" should contain:
      """
      # cloudfront_distribution_id:
      """
    Then the file "s3_website.yml" should contain:
      """
      # cloudfront_distribution_config:
      """
    Then the file "s3_website.yml" should contain:
      """
      # redirects:
      """
    Then the file "s3_website.yml" should contain:
      """
      # routing_rules:
      """
    Then the file "s3_website.yml" should contain:
      """
      # concurrency_level:
      """
    Then the file "s3_website.yml" should contain:
      """
      # extensionless_mime_type: text/html
      """

  @starts-new-os-process
  Scenario: Run s3_website with a malformed configuration file
    Given a directory named "_site"
    And a file named "s3_website.yml" with:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      this is not yaml
      """
    When I run `s3_website push`
    Then the output should contain:
      """
      I can't parse the file s3_website.yml. It should look like this:
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      """

  @starts-new-os-process
  Scenario: Run s3_website with a configuration file that does not contain a bucket
    Given a directory named "_site"
    And a file named "s3_website.yml" with:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket:
      """
    When I run `s3_website push`
    Then the output should contain:
      """
      I can't parse the file s3_website.yml. It should look like this:
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      """

  @new-files
  Scenario: Print the URL of the to the user
    When my S3 website is in "features/support/test_site_dirs/my.blog.com"
    And I call the push command
    Then the output should contain
      """
      Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html
      """
