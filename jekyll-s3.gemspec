# -*- encoding: utf-8 -*-
$:.push File.expand_path("../lib", __FILE__)
require "jekyll-s3/version"

Gem::Specification.new do |s|
  s.name        = "jekyll-s3"
  s.version     = Jekyll::S3::VERSION
  s.platform    = Gem::Platform::RUBY
  s.authors     = ["Philippe Creux"]
  s.email       = ["pcreux@gmail.com"]
  s.homepage    = ""
  s.summary     = %q{Push your jekyll blog to S3"}
  s.description = %q{Push your jekyll blog to AWS S3}

  s.default_executable = %q{jekyll-s3}

  s.add_dependency 'aws-s3'

  s.add_development_dependency 'rspec'
  s.add_development_dependency 'cucumber'
  s.add_development_dependency 'aruba'

  s.files         = `git ls-files`.split("\n")
  s.test_files    = `git ls-files -- {test,spec,features}/*`.split("\n")
  s.executables   = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.require_paths = ["lib"]
end
