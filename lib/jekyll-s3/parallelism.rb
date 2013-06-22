module Jekyll
  module S3
    class Parallelism
      def self.each_in_parallel_or_sequentially(items, &operation)
        if ENV['disable_parallel_processing']
          items.each do |item|
            operation.call item
          end
        else
          threads = items.map do |item|
            Thread.new(item) { |item|
              operation.call item
            }
          end
          threads.each { |thread| thread.join }
        end
      end
    end
  end
end
