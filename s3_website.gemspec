# -*- encoding: utf-8 -*-
require File.join([File.dirname(__FILE__),'lib','s3_website','version.rb'])

Gem::Specification.new do |s|
  s.name        = "s3_website"
  s.version     = S3Website::VERSION
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

  s.add_dependency 'thor', '~> 0.18'
  s.add_dependency 'configure-s3-website', '= 2.0.0'
  s.add_dependency 'colored', '1.2'
  s.add_dependency 'dotenv', '~> 1.0'

  s.add_development_dependency 'rake', '10.1.1'
  s.add_development_dependency 'octokit', '3.1.0'

  s.files         = `git ls-files`
                      .split("\n")
                      .reject { |f| f.match('sbt-launch.jar') } # Reject the SBT jar, as it is a big file
                      .push('resources/s3_website.jar.md5') # Include the checksum file in the gem
  s.test_files    = `git ls-files -- src/test/*`.split("\n")
  s.executables   = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.require_paths = ["lib"]
end
