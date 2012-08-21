require 'spec_helper'

describe Jekyll::S3::Uploader do
  describe "#upload_to_s3" do

    before :each do
      AWS::S3::Base.expects(:establish_connection!).at_least(1).returns true
      AWS::S3::Service.expects(:buckets).at_least(1).returns []
      AWS::S3::Bucket.expects(:create).at_least(1).returns true
      bucket = mock()
      bucket.expects(:objects).returns []
      AWS::S3::Bucket.expects(:find).at_least(1).returns bucket

      @uploader = Jekyll::S3::Uploader.new
      @uploader.expects(:local_files).at_least(1).returns ['index.html']
      @uploader.expects(:open).at_least(1).returns true
    end

    it "should work right when there are no exceptions" do
      AWS::S3::S3Object.expects(:store).at_least(1).returns(true)
      @uploader.send(:upload_to_s3!).should
    end

    it "should properly handle exceptions on uploading to S3" do
      AWS::S3::S3Object.expects(:store).raises(AWS::S3::RequestTimeout.new('timeout', 'timeout')).then.at_least(1).returns(true)
      @uploader.send(:upload_to_s3!).should
    end
  end

  describe "#call_cloudfront_invalidation" do
    it "should invalidate Cloudfront items if the configuration 'cloudfront_dist_id' exists" do
      configure_uploader({
        "s3_id" => "xx",
        "s3_secret" => "zz",
        "s3_bucket" => "bucket",
        "cloudfront_distribution_id" => "dist id"
      })
      Jekyll::Cloudfront::Invalidator.expects(:invalidate).with("xx", "zz", "bucket", "dist id")
      Jekyll::S3::Uploader.run!
    end

    it "should skip calling Cloudfront if the configuration 'cloudfront_dist_id' is missing" do
      configure_uploader({
        "s3_id" => "xx",
        "s3_secret" => "zz",
        "s3_bucket" => "bucket"
      })
      Jekyll::Cloudfront::Invalidator.expects(:invalidate).never
      Jekyll::S3::Uploader.run!
    end

    def configure_uploader(config)
      def disable_methods_that_interact_with_world
        Jekyll::S3::Uploader.any_instance.expects(:upload_to_s3!).returns nil
        Jekyll::S3::Uploader.any_instance.expects(:check_jekyll_project!).returns nil
        Jekyll::S3::Uploader.any_instance.expects(:check_s3_configuration!).returns nil
      end
      YAML.expects(:load_file).with('_jekyll_s3.yml').returns(config)
      disable_methods_that_interact_with_world
    end
  end
end
