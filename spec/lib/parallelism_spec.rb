require 'spec_helper'

describe S3Website::Parallelism do
  context 'user has disabled parallelism' do
    let(:config) {
      {}
    }

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
      S3Website::Parallelism.each_in_parallel_or_sequentially(ints, config) { |int|
        after_processing << int
      }
      ints.should eq(after_processing)
    end
  end

  context 'user has not disabled parallelism' do
    let(:config) {
      {}
    }

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
      S3Website::Parallelism.each_in_parallel_or_sequentially(ints, config) { |int|
        after_processing << int
      }
      ints.should_not eq(after_processing) # Parallel processing introduces non-determinism
    end
  end

  context 'limiting parallelism' do
    shared_examples 'parallel processing' do |config|
      let(:concurrency_level) {
        config['concurrency_level'] || S3Website::Parallelism::DEFAULT_CONCURRENCY_LEVEL
      }

      before(:each) {
        ints = (0..199).to_a
        @after_processing = []
        S3Website::Parallelism.each_in_parallel_or_sequentially(ints, config) { |int|
          @after_processing << int
        }
      }

      it "does at most <concurrency_level> operations in parallel" do
        @after_processing.slice(0, concurrency_level).all? do |int|
          int <= concurrency_level
        end.should be true
        @after_processing.slice(100, concurrency_level).all? do |int|
          int >= 100 and int <= 100 + concurrency_level
        end.should be true
      end
    end


    include_examples 'parallel processing', config = {}

    include_examples 'parallel processing', config = { 'concurrency_level' => 100 }
  end
end
