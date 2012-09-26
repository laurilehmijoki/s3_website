# -*- encoding: utf-8 -*-
$:.push File.expand_path("../lib", __FILE__)
require "jekyll-s3/version"

Gem::Specification.new do |s|
  s.name        = "jekyll-s3"
  s.version     = Jekyll::S3::VERSION
  s.platform    = Gem::Platform::RUBY
  s.authors     = ["Philippe Creux", "Lauri Lehmijoki"]
  s.email       = ["pcreux@gmail.com", "lauri.lehmijoki@iki.fi"]
  s.homepage    = "https://github.com/laurilehmijoki/jekyll-s3"
  s.summary     = %q{Push your Jekyll blog to S3}
  s.description = %q{This Gem allows you to push your Jekyll blog to AWS S3.
In addition, you can use this Gem to invalidate the related Cloudfront
distribution, making it easy to deliver your blog via the CDN.}

  s.default_executable = %q{jekyll-s3}

  s.add_dependency 'aws-sdk'
  s.add_dependency 'erubis'
  s.add_dependency 'simple-cloudfront-invalidator'

  s.add_development_dependency 'rspec'
  s.add_development_dependency 'rspec-expectations'
  s.add_development_dependency 'cucumber'
  s.add_development_dependency 'aruba', '>= 0.4.7'
  s.add_development_dependency 'rake'
  s.add_development_dependency 'vcr'
  s.add_development_dependency 'webmock'

  s.files         = `git ls-files`.split("\n")
  s.test_files    = `git ls-files -- {test,spec,features}/*`.split("\n")
  s.executables   = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.require_paths = ["lib"]
end
