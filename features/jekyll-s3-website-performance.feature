Feature: improve response times of your Jekyll website

  As a blogger
  I want to benefit from HTTP performance optimisations
  So that my readers would not have to wait long for my website to load

  @new-files
  Scenario: Set Cache-Control: max-age for all uploaded files
    When my Jekyll site is in "features/support/test_site_dirs/site.with.maxage.com"
    Then jekyll-s3 will push my blog to S3
    And the output should equal
      """
      Deploying _site/* to jekyll-s3-test.net
      Uploading 2 new file(s)
      Upload css/styles.css [max-age=120]: Success!
      Upload index.html [max-age=120]: Success!
      Done! Go visit: http://jekyll-s3-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @new-files
  Scenario: Set Cache-Control: max-age for CSS files only
    When my Jekyll site is in "features/support/test_site_dirs/site.with.css-maxage.com"
    Then jekyll-s3 will push my blog to S3
    And the output should equal
      """
      Deploying _site/* to jekyll-s3-test.net
      Uploading 2 new file(s)
      Upload css/styles.css [max-age=100]: Success!
      Upload index.html [max-age=0]: Success!
      Done! Go visit: http://jekyll-s3-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @new-files
  Scenario: Set Content-Encoding: gzip HTTP header
    When my Jekyll site is in "features/support/test_site_dirs/site.with.gzipped-html.com"
    Then jekyll-s3 will push my blog to S3
    And the output should equal
      """
      Deploying _site/* to jekyll-s3-test.net
      Uploading 2 new file(s)
      Upload css/styles.css: Success!
      Upload index.html [gzipped]: Success!
      Done! Go visit: http://jekyll-s3-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """

  @new-files
  Scenario: Set both the Content-Encoding: gzip and Cache-Control: max-age headers
    When my Jekyll site is in "features/support/test_site_dirs/site.with.gzipped-and-max-aged-content.com"
    Then jekyll-s3 will push my blog to S3
    And the output should equal
      """
      Deploying _site/* to jekyll-s3-test.net
      Uploading 2 new file(s)
      Upload css/styles.css [gzipped] [max-age=300]: Success!
      Upload index.html [gzipped] [max-age=300]: Success!
      Done! Go visit: http://jekyll-s3-test.net.s3-website-us-east-1.amazonaws.com/index.html

      """
