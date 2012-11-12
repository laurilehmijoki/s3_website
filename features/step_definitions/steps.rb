When /^my Jekyll site is in "(.*?)"$/ do |blog_dir|
  @blog_dir = blog_dir
end

When /^jekyll-s3 will push my blog to S3$/ do
  do_run
end

When /^the configuration contains the Cloudfront distribution id$/ do
  # Just here for readability
end

Then /^jekyll-s(\d+) will push my blog to S(\d+) and invalidate the Cloudfront distribution$/ do 
  |arg1, arg2|
  do_run
end

Then /^report that it uploaded (\d+) new and (\d+) changed files into S3$/ do
  |new_count, changed_count|
  raise unless @amount_of_new_files == new_count.to_i
  raise unless @amount_of_changed_files == changed_count.to_i
end

Then /^report that it deleted (\d+) file from S3$/ do |amount_of_deleted_files|
  raise unless @amount_of_deleted_files == amount_of_deleted_files.to_i
end

def do_run
  in_headless_mode = true
  @amount_of_new_files, @amount_of_changed_files, @amount_of_deleted_files =
    Jekyll::S3::CLI.new.run("#{@blog_dir}/_site", in_headless_mode)
end
