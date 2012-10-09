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

def do_run
  Jekyll::S3::CLI.new.run("#{@blog_dir}/_site")
end
