require 'spec_helper'

describe Jekyll::S3::Parallelism do
  context 'user has disabled parallelism' do
    before(:all) {
      @original_disable_state = ENV['disable_parallel_processing']
      ENV['disable_parallel_processing'] = 'true'
    }

    after(:all) {
      ENV['disable_parallel_processing'] = @original_disable_state
    }

    it 'runs things sequentially' do
      ints = (0..100).to_a
      after_processing = []
      Jekyll::S3::Parallelism.each_in_parallel_or_sequentially(ints) { |int|
        after_processing << int
      }
      ints.should eq(after_processing)
    end
  end

  context 'user has not disabled parallelism' do
    before(:all) {
      @original_disable_state = ENV['disable_parallel_processing']
      ENV.delete 'disable_parallel_processing'
    }

    after(:all) {
      ENV['disable_parallel_processing'] = @original_disable_state if @original_disable_state
    }

    it 'runs things in parallel' do
      ints = (0..100).to_a
      after_processing = []
      Jekyll::S3::Parallelism.each_in_parallel_or_sequentially(ints) { |int|
        after_processing << int
      }
      ints.should_not eq(after_processing) # Parallel processing introduces non-determinism
    end
  end
end
