require 'spec_helper'

describe Jekyll::S3::Retry do
  describe ".run_with_retry" do
    it "retry the operation 4 times" do
      retries = 0
      begin
        Jekyll::S3::Retry.run_with_retry(0.001) {
          retries += 1
          raise Exception
        }
      rescue
      end
      retries.should be(4)
    end

    it "throws an error if all retries fail" do
      expect {
        Jekyll::S3::Retry.run_with_retry(0.001) {
          raise Exception
        }
      }.to raise_error(Jekyll::S3::RetryAttemptsExhaustedError)
    end

    it "re-runs the block if the block throws an error" do
      retries = 0
      Jekyll::S3::Retry.run_with_retry(0.001) {
        retries += 1
        raise Exception if retries < 2
      }
      retries.should be(2)
    end
  end
end
