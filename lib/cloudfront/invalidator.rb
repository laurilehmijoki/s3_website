module Jekyll
  module Cloudfront
    class Invalidator
      def self.invalidate(
        aws_key, aws_secret, s3_bucket_name, cloudfront_distribution_id)
        s3 = AWS::S3.new(
          :access_key_id => aws_key,
          :secret_access_key => aws_secret)
        s3_object_keys = s3.buckets[s3_bucket_name].objects.map { |f| f.key }
        report = SimpleCloudfrontInvalidator::CloudfrontClient.new(
          aws_key, aws_secret, cloudfront_distribution_id).invalidate(
            s3_object_keys)
        puts report
      end
    end
  end
end
