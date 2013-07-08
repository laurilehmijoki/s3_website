require 'spec_helper'

describe S3Website::Parallelism do
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
      S3Website::Parallelism.each_in_parallel_or_sequentially(ints) { |int|
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
      S3Website::Parallelism.each_in_parallel_or_sequentially(ints) { |int|
        after_processing << int
      }
      ints.should_not eq(after_processing) # Parallel processing introduces non-determinism
    end
  end

  context 'limiting parallelism' do
    before(:each) {
      ints = (0..199).to_a
      @after_processing = []
      S3Website::Parallelism.each_in_parallel_or_sequentially(ints) { |int|
        @after_processing << int
      }
    }

    it "does at most #{S3Website::Parallelism::DEFAULT_CONCURRENCY_LEVEL} operations in parallel" do
      @after_processing.slice(0, 99).all? do |int|
        int <= 99
      end.should be true
      @after_processing.slice(100, 199).all? do |int|
        int >= 100 and int <= 199
      end.should be true
    end
  end
end
