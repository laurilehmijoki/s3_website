module S3Website
  class Parallelism
    def self.each_in_parallel_or_sequentially(items, &operation)
      if ENV['disable_parallel_processing']
        items.each do |item|
          operation.call item
        end
      else
        items.each_slice(DEFAULT_CONCURRENCY_LEVEL) { |items|
          threads = items.map do |item|
            Thread.new(item) { |item|
              operation.call item
            }
          end
          threads.each(&:join)
        }
      end
    end

    private

    DEFAULT_CONCURRENCY_LEVEL = 100
  end
end
