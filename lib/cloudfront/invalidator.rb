module Jekyll
  module Cloudfront
    class Invalidator
      def self.invalidate(
        aws_key, aws_secret, s3_bucket_name, cloudfront_distribution_id)
        bucket = AWS::S3::Bucket.find(s3_bucket_name)
        s3_object_keys = bucket.objects.map { |f| f.key }
        CloudfrontS3Invalidator::CloudfrontClient.new(
          aws_key, aws_secret, cloudfront_distribution_id).invalidate(
            s3_object_keys)
      end
    end
  end
end
