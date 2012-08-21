require 'rubygems'
require 'yaml'
require 'aws/s3'
require 'cf-s3-invalidator'

module Jekyll
  module S3
  end
end

%w{errors uploader cli}.each do |file|
  require File.dirname(__FILE__) + "/jekyll-s3/#{file}"
end

%w{invalidator}.each do |file|
  require File.dirname(__FILE__) + "/cloudfront/#{file}"
end
