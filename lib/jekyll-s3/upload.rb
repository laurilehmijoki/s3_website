module Jekyll
  module S3
    class Upload
      def initialize(path, s3, config, site_dir)
        @path = path
        @file = File.open("#{site_dir}/#{path}")
        @s3 = s3
        @config = config
      end

      def perform!
        @s3.buckets[@config['s3_bucket']].objects[@path].write(@file, upload_options)
      end

      def upload_options
        opts = {
          :content_type => mime_type,
          :reduced_redundancy => @config['s3_reduced_redundancy']
        }
      end

      def mime_type
        MIME::Types.type_for(@path).first
      end
    end
  end
end