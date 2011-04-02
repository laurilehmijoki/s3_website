module Jekyll
  module S3
    class CLI
      def self.run!
        Uploader.run!
      rescue JekyllS3Error => e
        puts e.message
        exit 1
      end
    end
  end
end
