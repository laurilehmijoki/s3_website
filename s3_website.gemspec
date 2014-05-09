# -*- encoding: utf-8 -*-
$:.push File.expand_path("../lib", __FILE__)

Gem::Specification.new do |s|
  s.name        = "s3_website_monadic"
  s.version     = "0.0.16"
  s.platform    = Gem::Platform::RUBY
  s.authors     = ["Lauri Lehmijoki"]
  s.email       = ["lauri.lehmijoki@iki.fi"]
  s.homepage    = "https://github.com/laurilehmijoki/s3_website/tree/s3_website_monadic"
  s.summary     = %q{Manage your S3 website}
  s.description = %q{
    Sync website files, set redirects, use HTTP performance optimisations, deliver via
    CloudFront.
  }
  s.license     = 'MIT'

  s.default_executable = %q{s3_website_monadic}

  s.add_dependency 'thor', '= 0.18.1'
  s.add_dependency 'configure-s3-website', '= 1.5.5'

  s.add_development_dependency 'rake', '10.1.1'

  s.files         = `git ls-files`.split("\n")
  s.test_files    = `git ls-files -- {test,spec,features}/*`.split("\n")
  s.executables   = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.require_paths = ["lib"]
end
