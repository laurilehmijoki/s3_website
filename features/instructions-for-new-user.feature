Feature: Instructions for a new user

  As a new Jekyll-s3 user
  I would like to get helpful feedback when running `jekyll-s3`
  So that I can upload my Jekyll site to S3 without headache

  Scenario: Run jekyll-s3 in the wrong directory
    When I run `jekyll-s3`
    Then the output should contain:
      """
      I can't find any directory called _site. Are you in the right directory?
      """

  Scenario: Run jekyll-s3 for the first time
    Given a directory named "_site"
    When I run `jekyll-s3`
    Then the output should contain:
      """
      I've just generated a file called _jekyll_s3.yml. Go put your details in it!
      """
    Then the file "_jekyll_s3.yml" should contain:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      """

  Scenario: Run jekyll-s3 with an empty configuration file
    Given a directory named "_site"
    And an empty file named "_jekyll_s3.yml"
    When I run `jekyll-s3`
    Then the output should contain:
      """
      I can't parse the file _jekyll_s3.yml. It should look like this:
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      """

  Scenario: Run jekyll-s3 with a malformed configuration file
    Given a directory named "_site"
    And a file named "_jekyll_s3.yml" with:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      this is not yaml
      """
    When I run `jekyll-s3`
    Then the output should contain:
      """
      I can't parse the file _jekyll_s3.yml. It should look like this:
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      """

  Scenario: Run jekyll-s3 with a configuration file that does not contain a bucket
    Given a directory named "_site"
    And a file named "_jekyll_s3.yml" with:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket:
      """
    When I run `jekyll-s3`
    Then the output should contain:
      """
      I can't parse the file _jekyll_s3.yml. It should look like this:
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      """

  @new-files
  Scenario: Print the URL of the site to the user
    When my Jekyll site is in "features/support/test_site_dirs/my.blog.com"
    Then jekyll-s3 will push my blog to S3
    And the output should contain
      """
      Go visit: http://jekyll-s3-test.net.s3-website-us-east-1.amazonaws.com/index.html
      """
