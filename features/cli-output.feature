Feature: Command-line interface feedback

  Scenario: Run jekyll-s3 in the wrong directory
    When I run `jekyll-s3`
    Then the output should contain:
      """
      I can't find any directory called _site. Are you in the right directory?
      """

  # For some reason this scenario fails on Travis but on on localhost
  @skip-on-travis
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
      s3_reduced_redundancy: false
      cloudfront_distribution_id: YOUR_CLOUDFRONT_DIST_ID (OPTIONAL)
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
      s3_reduced_redundancy: false
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
      s3_reduced_redundancy: false
      """

  Scenario: Run jekyll-s3 with a configuration file that does not contain a bucket
    Given a directory named "_site"
    And a file named "_jekyll_s3.yml" with:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket:
      s3_reduced_redundancy: false
      """
    When I run `jekyll-s3`
    Then the output should contain:
      """
      I can't parse the file _jekyll_s3.yml. It should look like this:
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      s3_reduced_redundancy: false
      """

  Scenario: Run jekyll-s3
    Given a directory named "_site"
    And a file named "_jekyll_s3.yml" with:
      """
      s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
      s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
      s3_bucket: your.blog.bucket.com
      s3_reduced_redundancy: false
      """
    When I run `jekyll-s3`
    Then the output should contain:
      """
      Deploying _site/* to your.blog.bucket.com
      """
