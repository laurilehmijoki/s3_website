module S3Website
  module Cloudfront
    class Invalidator
      def self.invalidate(config, changed_files)
        aws_key = config['s3_id']
        aws_secret = config['s3_secret']
        cloudfront_distribution_id = config['cloudfront_distribution_id']
        s3_object_keys = apply_config config, changed_files
        s3_object_keys << ""
        report = SimpleCloudfrontInvalidator::CloudfrontClient.new(
          aws_key, aws_secret, cloudfront_distribution_id
        ).invalidate(url_encode_keys s3_object_keys)
        puts report[:text_report]
        report[:invalidated_items_count]
      end

      private

      def self.url_encode_keys(keys)
        require 'uri'
        keys.map do |key|
          URI::encode(key, Regexp.union([URI::Parser.new.regexp[:UNSAFE],'~','@', "'"]))
        end
      end

      def self.apply_config(config, changed_files)
        if config['cloudfront_invalidate_root']
          changed_files.map { |changed_file|
            changed_file.sub /\/index.html$/, '/'
          }
        else
          changed_files
        end
      end
    end
  end
end
