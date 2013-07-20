module S3Website
  class Parallelism
    def self.each_in_parallel_or_sequentially(items, config, &operation)
      if ENV['disable_parallel_processing']
        items.each do |item|
          operation.call item
        end
      else
        slice_size = config['concurrency_level'] || DEFAULT_CONCURRENCY_LEVEL
        items.each_slice(slice_size) { |items|
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

    DEFAULT_CONCURRENCY_LEVEL = 25
  end
end
