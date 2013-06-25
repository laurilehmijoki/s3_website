require 'spec_helper'

describe S3Website::ConfigLoader do
  it 'supports eRuby syntax in s3_website.yml' do
    config = S3Website::ConfigLoader.load_configuration('spec/sample_files/hyde_site/')
    config['s3_id'].should eq('hello')
    config['s3_secret'].should eq('world')
    config['s3_bucket'].should eq('galaxy')
  end

  it 'does not define default endpoint' do
    config = S3Website::ConfigLoader.load_configuration('spec/sample_files/hyde_site/')
    config['s3_endpoint'].should be_nil
  end

  it 'reads the S3 endpoint setting from s3_website.yml' do
    config = S3Website::ConfigLoader.load_configuration('spec/sample_files/tokyo_site')
    config['s3_endpoint'].should eq('ap-northeast-1')
  end
end
