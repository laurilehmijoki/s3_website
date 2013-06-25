module S3Website
  class S3WebsiteError < StandardError
  end

  class NoWebsiteDirectoryFound < S3WebsiteError
    def initialize(message = "I can't find any website. Are you in the right directory?")
      super(message)
    end
  end

  class NoPredefinedWebsiteDirectoryFound < NoWebsiteDirectoryFound
    def initialize(message = "I can't find a website in any of the following directories: #{Paths.site_paths.join(', ')}. Please specify the location of the website with the --site option.")
      super(message)
    end
  end

  class NoConfigurationFileError < S3WebsiteError
    def initialize(message = "I've just generated a file called s3_website.yml. Go put your details in it!")
      super(message)
    end
  end

  class MalformedConfigurationFileError < S3WebsiteError
    def initialize(message = "I can't parse the file s3_website.yml. It should look like this:\n#{ConfigLoader::CONFIGURATION_FILE_TEMPLATE}")
      super(message)
    end
  end

  class RetryAttemptsExhaustedError < S3WebsiteError
    def initialize(message = "Operation failed even though we tried to recover from it")
      super(message)
    end
  end
end
