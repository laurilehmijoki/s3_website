require 'spec_helper'

describe S3Website::Cloudfront::Invalidator do
  describe 'default behaviour' do
    let(:config) {{
      's3_id' => 'aws id',
      's3_secret' => 'aws secret',
      'cloudfront_distribution_id' => 'EFXX'
    }}

    it 'invalidates the root resource' do
      invalidator = create_simple_cloudfront_invalidator(config)
      invalidator.
        should_receive(:invalidate).
        with(['index.html', '']).
        and_return(:text_report => 'report txt')

      S3Website::Cloudfront::Invalidator.invalidate(config, ['index.html'])
    end
  end

  describe 'option cloudfront_invalidate_root = true' do
    let(:config) {{
      's3_id' => 'aws id',
      's3_secret' => 'aws secret',
      'cloudfront_distribution_id' => 'EFXX',
      'cloudfront_invalidate_root' => true
    }}

    it 'invalidates all root resources' do
      invalidator = create_simple_cloudfront_invalidator(config)
      invalidator.
        should_receive(:invalidate).
        with(['article/', '']).
        and_return(:text_report => 'report txt')

      S3Website::Cloudfront::Invalidator.invalidate(config, ['article/index.html'])
    end
  end

  def create_simple_cloudfront_invalidator(config)
    invalidator = double('invalidator')
    SimpleCloudfrontInvalidator::CloudfrontClient.
      should_receive(:new).
      with(config['s3_id'], config['s3_secret'], config['cloudfront_distribution_id']).
      and_return(invalidator)
    invalidator
  end
end
