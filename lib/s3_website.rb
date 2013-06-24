require 'rubygems'
require 'yaml'
require 'erubis'
require 'aws-sdk'
require 'simple-cloudfront-invalidator'
require 'filey-diff'
require 'mime/types'

module S3Website
  DEFAULT_GZIP_EXTENSIONS = %w(.html .css .js .svg .txt)
end

%w{errors upload uploader cli config_loader retry keyboard diff_helper endpoint parallelism}.each do |file|
  require File.dirname(__FILE__) + "/s3_website/#{file}"
end

%w{invalidator}.each do |file|
  require File.dirname(__FILE__) + "/cloudfront/#{file}"
end
