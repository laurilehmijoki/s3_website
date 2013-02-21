require 'tempfile'
require 'zlib'

module Jekyll
  module S3
    class Upload
      attr_reader :config, :file, :path, :full_path, :s3

      def initialize(path, s3, config, site_dir)
        @path = path
        @full_path = "#{site_dir}/#{path}"
        @file = File.open("#{site_dir}/#{path}")
        @s3 = s3
        @config = config
      end

      def perform!
        success = s3.buckets[config['s3_bucket']].objects[path].write(upload_file, upload_options)
        upload_file.close
        success
      end

      def upload_file
        @upload_file ||= gzip? ? gzipped_file : file
      end

      def gzip?
        return false unless !!config['gzip']

        extensions = config['gzip'].is_a?(Array) ? config['gzip'] : Jekyll::S3::DEFAULT_GZIP_EXTENSIONS
        extensions.include?(File.extname(path))
      end

      def gzipped_file
        tempfile = Tempfile.new(File.basename(path))
        
        gz = Zlib::GzipWriter.new(tempfile, Zlib::BEST_COMPRESSION, Zlib::DEFAULT_STRATEGY)
        
        gz.mtime = File.mtime(full_path)
        gz.orig_name = File.basename(path)
        gz.write(file.read)
        
        gz.flush
        tempfile.flush
        
        gz.close
        tempfile.open

        tempfile
      end

      def upload_options
        opts = {
          :content_type => mime_type,
          :reduced_redundancy => config['s3_reduced_redundancy']
        }

        opts[:content_encoding] = "gzip" if gzip?
        opts
      end

      def mime_type
        MIME::Types.type_for(path).first
      end
    end
  end
end