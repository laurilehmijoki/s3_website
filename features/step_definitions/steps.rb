When /^my Jekyll site is in "(.*?)"$/ do |blog_dir|
  @blog_dir = blog_dir
end

When /^jekyll-s3 will push my blog to S3$/ do
  do_run
end

Then /^jekyll-s(\d+) will push my blog to S(\d+) and invalidate the Cloudfront distribution$/ do
  |arg1, arg2|
  do_run
end

Then /^the output should equal$/ do |expected_console_output|
  @console_output.should eq(expected_console_output)
end

def do_run
  @console_output = capture_stdout {
    in_headless_mode = true
    result = Jekyll::S3::CLI.new.run("#{@blog_dir}/_site", in_headless_mode)
    @amount_of_new_files = result[:new_files_count]
    @amount_of_changed_files = result[:changed_files_count]
    @amount_of_deleted_files = result[:deleted_files_count]
    @amount_of_invalidated_items = result[:invalidated_items_count]
  }
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
