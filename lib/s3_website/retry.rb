module S3Website
  class Retry
    def self.run_with_retry(sleep_milliseconds = 3.000)
      attempt = 0
      begin
        yield
      rescue Exception => e
        $stderr.puts "Exception Occurred:  #{e.message} (#{e.class})  Retrying in 3 seconds..."
        sleep sleep_milliseconds
        attempt += 1
        if attempt <= 3
          retry
        else
          raise RetryAttemptsExhaustedError
        end
      end
    end
  end
end
