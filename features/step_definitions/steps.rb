require 'rspec'

When /^my S3 website is in "(.*?)"$/ do |blog_dir|
  @blog_dir = blog_dir
end

When /^s3_website will push my blog to S3$/ do
  push_files
end

Then /^s3_website will push my blog to S(\d+) and invalidate the Cloudfront distribution$/ do |args|
  push_files
end

Then /^the output should equal$/ do |expected_console_output|
  @console_output.should eq(expected_console_output)
end

Then /^the output should contain$/ do |expected_console_output|
  @console_output.should include(expected_console_output)
end

Then /^report that it uploaded (\d+) new and (\d+) changed files into S3$/ do
  |new_count, changed_count|
  @amount_of_new_files.should == new_count.to_i
  @amount_of_changed_files.should == changed_count.to_i
end

Then /^report that it invalidated (\d+) Cloudfront item$/ do |expected|
  @amount_of_invalidated_items.should == expected.to_i
end

Then /^report that it created (\d+) new redirects$/ do |expected|
  @amount_of_new_redirects.should == expected.to_i
end

Then /^report that it deleted (\d+) file from S3$/ do |amount_of_deleted_files|
  @amount_of_deleted_files.should == amount_of_deleted_files.to_i
end

def push_files
  @console_output = capture_stdout {
    in_headless_mode = true
    site_path = S3Website::Paths.infer_site_path(
      'infer automatically',
      @blog_dir
    )
    result = S3Website::Tasks.push(
      @blog_dir,
      site_path,
      in_headless_mode
    )
    @amount_of_new_files = result[:new_files_count]
    @amount_of_changed_files = result[:changed_files_count]
    @amount_of_deleted_files = result[:deleted_files_count]
    @amount_of_invalidated_items = result[:invalidated_items_count]
    @amount_of_new_redirects = result[:changed_redirects_count]
  }
end

module S3Website
  class DiffHelper
    class DiffProgressIndicator
      def initialize(start_msg, end_msg)
        puts "#{start_msg} #{end_msg}"
      end

      def render_next_step
        # Simplify testing of stdout by doing nothing here.
      end

      def finish
        # Simplify testing of stdout by doing nothing here.
      end
    end
  end
end

module Kernel
  require 'stringio'

  def capture_stdout
    out = StringIO.new
    $stdout = out
    yield
    out.string
  ensure
    $stdout = STDOUT
  end
end
