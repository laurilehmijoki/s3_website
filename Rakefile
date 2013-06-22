require 'bundler'
Bundler::GemHelper.install_tasks

desc "Build the project"
task :default => 'test'

desc "Run tests"
task :test do
  ENV['disable_parallel_processing'] = 'true'
  sh "bundle exec rspec"
  sh "bundle exec cucumber --tags ~@skip-on-travis"
end

desc 'Run work-in-progress features'
task "cucumber:wip" do
  ENV['disable_parallel_processing'] = 'true'
  sh "bundle exec cucumber --tags @wip"
end
