When /^my Jekyll site is in "(.*?)"$/ do |blog_dir|
  @blog_dir = blog_dir
end

When /^jekyll-s3 will push my blog to S3$/ do
  do_run
end

When /^the configuration contains the Cloudfront distribution id$/ do
  # Just here for readability
end

Then /^jekyll-s(\d+) will push my blog to S(\d+) and invalidate the Cloudfront distribution$/ do |arg1, arg2|
  do_run
end

Then /^report that it uploaded (\d+) files into S3$/ do |upload_count_expectation|
  raise unless @amount_of_uploaded_files == upload_count_expectation.to_i
end

def do_run
  @amount_of_uploaded_files = Jekyll::S3::CLI.new.run("#{@blog_dir}/_site")
end
