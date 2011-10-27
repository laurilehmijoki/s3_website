require  File.dirname(__FILE__) + "/../../lib/jekyll-s3.rb"

RSpec.configure do |config|
  config.mock_framework = :mocha
end

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
      AWS::S3::S3Object.expects(:store).raises(Exception).then.at_least(1).returns(true)
      @uploader.send(:upload_to_s3!).should
    end
  end
end