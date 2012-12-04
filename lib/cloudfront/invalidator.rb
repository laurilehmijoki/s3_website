module Jekyll
  module Cloudfront
    class Invalidator
      def self.invalidate(config, changed_files)
        aws_key = config['s3_id']
        aws_secret = config['s3_secret']
        s3_bucket_name = config['s3_bucket']
        cloudfront_distribution_id = config['cloudfront_distribution_id']
        s3 = AWS::S3.new(
          :access_key_id => aws_key,
          :secret_access_key => aws_secret)
        s3_object_keys = changed_files
        s3_object_keys << ""
        report = SimpleCloudfrontInvalidator::CloudfrontClient.new(
          aws_key, aws_secret, cloudfront_distribution_id).invalidate(
            s3_object_keys)
        puts report
      end
    end
  end
end
