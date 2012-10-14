Feature: jekyll-s3

  In order to push my jekyll site to s3
  As a blogger
  I want to run jekyll-s3 and say OMG it just worked!

  @new-files
  Scenario: Push a new Jekyll site to S3
    When my Jekyll site is in "spec/test_site_dirs/my.blog.com"
    Then jekyll-s3 will push my blog to S3
    And report that it uploaded 2 files into S3

  @new-and-changed-files
  Scenario: Upload a new blog post and change an old post
    When my Jekyll site is in "spec/test_site_dirs/new-and-changed-files.com"
    Then jekyll-s3 will push my blog to S3
    And report that it uploaded 2 files into S3

  @only-changed-files
  Scenario: Update an existing blog post
    When my Jekyll site is in "spec/test_site_dirs/only-changed-files.com"
    Then jekyll-s3 will push my blog to S3
    And report that it uploaded 1 files into S3

  @no-new-or-changed-files
  Scenario: The user runs jekyll-s3 even though he doesn't have new or changed posts
    When my Jekyll site is in "spec/test_site_dirs/no-new-or-changed-files.com"
    Then jekyll-s3 will push my blog to S3
    And report that it uploaded 0 files into S3
