require 'spec_helper'

describe Jekyll::S3::ConfigLoader do
  it 'supports eRuby syntax in _jekyll_s3.yml' do
    config = Jekyll::S3::ConfigLoader.load_configuration('spec/sample_files/hyde_site/_site')
    config['s3_id'].should eq('hello')
    config['s3_secret'].should eq('world')
    config['s3_bucket'].should eq('galaxy')
  end

  it 'reads the S3 endpoint setting from _jekyll_s3.yml' do
    config = Jekyll::S3::ConfigLoader.load_configuration('spec/sample_files/tokyo_site/_site')
    config['s3_endpoint'].should eq('ap-northeast-1')
  end
end
