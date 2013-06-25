Feature: displaying help text in the command-line interface

  As a user
  I want to see the available commands and options right on CLI
  So that I don't have to browse the documentation on the web

  @starts-new-os-process
  Scenario: User wants to know what he can do with s3_website
    When I run `s3_website`
    Then the output should contain:
      """
      Commands:
        s3_website cfg SUBCOMMAND ...ARGS  # Operate on the config file
        s3_website help [COMMAND]          # Describe available commands or one spe...
        s3_website push                    # Push local files with the S3 website
      """

  @starts-new-os-process
  Scenario: User wants to know what the push command does
    When I run `s3_website help push`
    Then the output should contain:
      """
      Usage:
        s3_website push

      Options:
        [--site=SITE]  # The directory where your website files are. When not defined, s3_website will look for the site in public/output or _site.
                       # Default: infer automatically
        [--headless]   # When headless, s3_website will not require human interaction at any point

      Description:
        `s3_website push` will upload new and changes files to S3. It will also delete
        from S3 the files that you no longer have locally.
      """

  @starts-new-os-process
  Scenario: User wants to know what the cfg command does
    When I run `s3_website cfg`
    Then the output should contain:
      """
      Commands:
        s3_website cfg apply           # Apply the configuration on the AWS services
        s3_website cfg create          # Create a config file with placeholder values
        s3_website cfg help [COMMAND]  # Describe subcommands or one specific subco...
      """

  @starts-new-os-process
  Scenario: User wants to know what the cfg apply command does
    When I run `s3_website cfg help apply`
    Then the output should contain:
      """
      Usage:
        s3_website apply

      Description:
        `s3_website cfg apply` will apply the configuration the S3 bucket.

        In addition, if you CloudFront related settings, this command will apply them
        on the CloudFront distribution.

        If the S3 bucket does not exist, this command will create it and configure it
        to function as a website.
      """

  @starts-new-os-process
  @wip
  Scenario: User wants to know what the cfg create command does
    When I run `s3_website cfg help create`
    Then the output should contain:
      """
      Usage:
        s3_website create

      Create a config file with placeholder values
      """
