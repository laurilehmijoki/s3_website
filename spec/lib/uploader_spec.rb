require 'spec_helper'

describe Jekyll::S3::Uploader do
  describe "#upload" do
    before do
      @uploader = Jekyll::S3::Uploader.new("", "", "", "", "")
    end

    it "should retry to upload a few times" do
      expect {
        @uploader.send(:upload, nil, nil)
      }.to raise_error(Jekyll::S3::RetryAttemptsExhaustedError)
    end
  end
end
