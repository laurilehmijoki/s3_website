require 'rubygems'
require 'yaml'
require 'aws/s3'

module Jekyll
  module S3
  end
end

%w{errors uploader cli}.each do |file|
  require File.dirname(__FILE__) + "/jekyll-s3/#{file}"
end
