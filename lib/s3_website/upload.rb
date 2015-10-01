require 'tempfile'
require 'zlib'
require 'zopfli'

module S3Website
  class Upload
    attr_reader :config, :file, :path, :full_path, :s3
    BLACKLISTED_FILES = %r{/?s3_website.yml$}, %r{/?\.env$}

    def initialize(path, s3, config, site_dir)
      raise "May not upload #{path}, because it's blacklisted" if Upload.is_blacklisted(path, config)
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

    def self.is_blacklisted(path, config)
      [
        config['exclude_from_upload'],
        BLACKLISTED_FILES
      ].flatten.compact.any? do |blacklisted_file|
        Regexp.new(blacklisted_file).match path
      end
    end

    private

    def upload_file
      @upload_file ||= file
    end

    def gzip?
      return false unless !!config['gzip']

      extensions = config['gzip'].is_a?(Array) ? config['gzip'] : S3Website::DEFAULT_GZIP_EXTENSIONS
      extensions.include?(File.extname(path))
    end

    def upload_options
      opts = {}
      opts[:reduced_redundancy] = config['s3_reduced_redundancy']
      opts[:content_type] = resolve_content_type
      opts[:content_encoding] = "gzip" if gzip?
      opts[:cache_control] = cache_control_value if cache_control?
      opts
    end

    def resolve_content_type
      is_text = mime_type.start_with?('text/') || mime_type == 'application/json'
      if is_text
        "#{mime_type}; charset=utf-8" # Let's consider UTF-8 as the de-facto encoding standard for text
      else
        mime_type
      end
    end

    def cache_control_value
      if max_age == 0
        "no-cache, max-age=0"
      else
        "max-age=#{max_age}"
      end
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
      if !!config['extensionless_mime_type'] && File.extname(path) == ""
        config['extensionless_mime_type']
      else
        MIME::Types.type_for(path).first.to_s
      end
    end
  end
end
