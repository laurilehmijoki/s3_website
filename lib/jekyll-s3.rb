require 'rubygems'
require 'yaml'
require 'aws-sdk'
require 'simple-cloudfront-invalidator'

module Jekyll
  module S3
  end
end

%w{errors uploader cli config_loader retry keyboard}.each do |file|
  require File.dirname(__FILE__) + "/jekyll-s3/#{file}"
end

%w{invalidator}.each do |file|
  require File.dirname(__FILE__) + "/cloudfront/#{file}"
end
