Feature: reporting errors to the user

  Background: we want to show a non-technical error report to the user.
    Consequently, we do not print the stack trace of the error.

  @starts-new-os-process
  @network-io
  Scenario: The user calls "push" when the S3 credentials are invalid
    When I run `s3_website push --site ../../features/support/test_site_dirs/my.blog.com --config_dir ../../features/support/test_site_dirs/my.blog.com`
    Then the output should contain:
      """
      The AWS Access Key Id you provided does not exist in our records. (AWS::S3::Errors::InvalidAccessKeyId)
      """
    And the output should not contain:
      """
      throw
      """
    And the exit status should be 1

  @starts-new-os-process
  @network-io
  Scenario: The user calls "cfg apply" when the S3 credentials are invalid
    When I run `s3_website cfg apply`
    And the exit status should be 1
