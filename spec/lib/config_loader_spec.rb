require 'spec_helper'

describe Jekyll::S3::ConfigLoader do
  it 'supports eRuby syntax in _jekyll_s3.yml' do
    config = Jekyll::S3::ConfigLoader.load_configuration('spec/sample_files/hyde_site/_site')
    config['s3_id'].should eq('hello')
    config['s3_secret'].should eq('world')
    config['s3_bucket'].should eq('galaxy')
  end
end
