require 'spec_helper'

describe S3Website::Retry do
  describe ".run_with_retry" do
    it "retry the operation 4 times" do
      retries = 0
      begin
        S3Website::Retry.run_with_retry(0.001) {
          retries += 1
          raise Exception
        }
      rescue
      end
      retries.should be(4)
    end

    it "throws an error if all retries fail" do
      expect {
        S3Website::Retry.run_with_retry(0.001) {
          raise Exception
        }
      }.to raise_error(S3Website::RetryAttemptsExhaustedError)
    end

    it "re-runs the block if the block throws an error" do
      retries = 0
      S3Website::Retry.run_with_retry(0.001) {
        retries += 1
        raise Exception if retries < 2
      }
      retries.should be(2)
    end
  end
end
