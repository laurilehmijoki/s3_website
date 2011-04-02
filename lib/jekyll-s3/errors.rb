module Jekyll
  module S3
    class JekyllS3Error < StandardError
    end

    class NotAJekyllProjectError < JekyllS3Error
      def initialize(message = "I can't find any directory called _site. Are you in the right directory?")
        super(message)
      end
    end

    class NoConfigurationFileError < JekyllS3Error
      def initialize(message = "I've just generated a file called _jekyll_s3.yml. Go put your details in it!")
        super(message)
      end
    end

    class MalformedConfigurationFileError < JekyllS3Error
      def initialize(message = "I can't parse the file _jekyll_s3.yml. It should look like this:\n#{Uploader::CONFIGURATION_FILE_TEMPLATE}")
        super(message)
      end
    end

  end
end
