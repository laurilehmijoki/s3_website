require 'spec_helper'

describe Jekyll::Cloudfront::Invalidator do
  describe "#invalidate cloudfront items" do
    it "should retrieve all objects from the S3 bucket and call invalidation on them" do
      s3_object_keys = ["key1", "key2"]
      s3_objects = s3_object_keys.map { |key| S3Object.new(key) }
      @s3_bucket_name = "my-s3-bucket"
      AWS::S3::Bucket.expects(:find).with(@s3_bucket_name).returns(S3Bucket.new(s3_objects))
      SimpleCloudfrontInvalidator::CloudfrontClient.any_instance.
        expects(:invalidate).with(s3_object_keys)

      Jekyll::Cloudfront::Invalidator.invalidate("", "", @s3_bucket_name, "")
    end
  end
end

class S3Bucket
  def initialize(s3_objects)
    @s3_objects = s3_objects
  end

  def objects
    @s3_objects
  end
end

class S3Object
  def initialize(key)
    @key = key
  end

  def key
    @key
  end
end
