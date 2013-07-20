require 'tempfile'
require 'zlib'

module S3Website
  class Upload
    attr_reader :config, :file, :path, :full_path, :s3
    BLACKLISTED_FILES = ['s3_website.yml']

    def initialize(path, s3, config, site_dir)
      raise "May not upload #{path}, because it's blacklisted" if Upload.is_blacklisted path
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

    def details
      "#{path}#{" [gzipped]" if gzip?}#{" [max-age=#{max_age}]" if cache_control?}"
    end

    def self.is_blacklisted(path)
      BLACKLISTED_FILES.any? do |blacklisted_file|
        path.include? blacklisted_file
      end
    end

    private

    def upload_file
      @upload_file ||= gzip? ? gzipped_file : file
    end

    def gzip?
      return false unless !!config['gzip']

      extensions = config['gzip'].is_a?(Array) ? config['gzip'] : S3Website::DEFAULT_GZIP_EXTENSIONS
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

      opts[:content_type] = "text/html; charset=utf-8" if mime_type == 'text/html'
      opts[:content_encoding] = "gzip" if gzip?
      opts[:cache_control] = "max-age=#{max_age}" if cache_control?

      opts
    end

    def cache_control?
      !!config['max_age']
    end

    def max_age
      if config['max_age'].is_a?(Hash)
        max_age_entries_most_specific_first.each do |glob_and_age|
          (glob, age) = glob_and_age
          return age if File.fnmatch(glob, path)
        end
      else
        return config['max_age']
      end

      return 0
    end

    # The most specific max-age glob == the longest glob
    def max_age_entries_most_specific_first
      sorted_by_glob_length = config['max_age'].
        each_pair.
        to_a.
        sort_by do |glob_and_age|
          (glob, age) = glob_and_age
          sort_key = glob.length
        end.
        reverse
    end

    def mime_type
      MIME::Types.type_for(path).first
    end
  end
end
