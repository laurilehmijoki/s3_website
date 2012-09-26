require 'rubygems'
require 'yaml'
require 'erubis'
require 'aws-sdk'
require 'simple-cloudfront-invalidator'

module Jekyll
  module S3
  end
end

%w{errors uploader cli config_loader}.each do |file|
  require File.dirname(__FILE__) + "/jekyll-s3/#{file}"
end

%w{invalidator}.each do |file|
  require File.dirname(__FILE__) + "/cloudfront/#{file}"
end
