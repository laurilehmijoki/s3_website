# -*- encoding: utf-8 -*-
$:.push File.expand_path("../lib", __FILE__)

Gem::Specification.new do |s|
  s.name        = "s3_website"
  s.version     = "1.7.6"
  s.platform    = Gem::Platform::RUBY
  s.authors     = ["Lauri Lehmijoki"]
  s.email       = ["lauri.lehmijoki@iki.fi"]
  s.homepage    = "https://github.com/laurilehmijoki/s3_website"
  s.summary     = %q{Manage your S3 website}
  s.description = %q{
    Sync website files, set redirects, use HTTP performance optimisations, deliver via
    CloudFront.
  }
  s.license     = 'MIT'

  s.default_executable = %q{s3_website}

  s.add_dependency 'aws-sdk', '~> 1'
  s.add_dependency 'filey-diff', '~> 2.0.0'
  s.add_dependency 'simple-cloudfront-invalidator', '~> 1'
  s.add_dependency 'erubis', '~> 2.7.0'
  s.add_dependency 'mime-types', '~> 1'
  s.add_dependency 'thor', '= 0.18.1'
  s.add_dependency 'configure-s3-website', '= 1.5.5'
  s.add_dependency 'zopfli', '~> 0.0.3'

  s.add_development_dependency 'rspec', '2.14.0'
  s.add_development_dependency 'rspec-expectations', '2.14.4'
  s.add_development_dependency 'cucumber', '1.3.10'
  s.add_development_dependency 'aruba', '0.5.3'
  s.add_development_dependency 'rake', '10.1.1'
  s.add_development_dependency 'vcr', '2.8.0'
  s.add_development_dependency 'webmock', '1.16.1'

  s.files         = `git ls-files`.split("\n")
  s.test_files    = `git ls-files -- {test,spec,features}/*`.split("\n")
  s.executables   = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.require_paths = ["lib"]
end
