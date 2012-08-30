When /^my Jekyll site is in "(.*?)"$/ do |blog_dir|
  @blog_dir = blog_dir
end

When /^jekyll-s3 will push my blog to S3$/ do
  Jekyll::S3::CLI.new.run!("#{@blog_dir}/_site")
end
