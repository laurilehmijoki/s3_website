require 'rubygems'
require 'yaml'
require 'erubis'
require 'aws-sdk'
require 'simple-cloudfront-invalidator'
require 'filey-diff'
require 'mime/types'

module Jekyll
  module S3
  	DEFAULT_GZIP_EXTENSIONS = %w(.html .css .js .svg .txt)
  end
end

%w{errors upload uploader cli config_loader retry keyboard diff_helper endpoint}.each do |file|
  require File.dirname(__FILE__) + "/jekyll-s3/#{file}"
end

%w{invalidator}.each do |file|
  require File.dirname(__FILE__) + "/cloudfront/#{file}"
end
