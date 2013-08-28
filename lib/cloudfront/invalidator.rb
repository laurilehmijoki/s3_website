module S3Website
  module Cloudfront
    class Invalidator
      def self.invalidate(config, changed_files)
        aws_key = config['s3_id']
        aws_secret = config['s3_secret']
        cloudfront_distribution_id = config['cloudfront_distribution_id']
        s3_object_keys = changed_files
        s3_object_keys << ""
        report = SimpleCloudfrontInvalidator::CloudfrontClient.new(
          aws_key, aws_secret, cloudfront_distribution_id
        ).invalidate(s3_object_keys)
        puts report[:text_report]
        report[:invalidated_items_count]
      end
    end
  end
end
