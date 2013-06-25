Feature: Instructions for a new user

  As a new s3_website user
  I would like to get helpful feedback when running `s3_website`
  So that I can upload my S3 website to S3 without headache

  Scenario: Run s3_website in the wrong directory
    When I run `s3_website push`
    Then the output should contain:
      """
      I can't find a website in any of the following directories: public/output, _site. Please specify the location of the website with the --site option.
      """

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
    Then s3_website will push my blog to S3
    And the output should contain
      """
      Go visit: http://s3-website-test.net.s3-website-us-east-1.amazonaws.com/index.html
      """
