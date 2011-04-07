# jekyll-s3

Deploy your jekyll site to S3.

## Install

    gem install jekyll-s3

## Setup

  * Go to your jekyll site directory
  * Run `jekyll-s3`. It generates a configuration file called `_jekyll_s3.yml` that looks like that:
<pre>
s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
s3_bucket: your.blog.bucket.com
</pre>

  * Edit it with your details.

## Deploy!

  * Run `jekyll-s3`. Done.

## Want `http://www.my-awesome-blog.com/` to render `'index.html'`?
  
  * Setup Cloudfront to distribute your s3 bucket files (you can do that
    through the [AWS management console](https://console.aws.amazon.com/s3/home))
  * Set the cloudfront root object to index.html (you can use the
    [aws-cloudfront gem](https://github.com/iltempo/aws-cloudfront) to do so)

## Todo

  * Upload new / updated files *only*
  * Setup Cloudfront distribution
  * Invalidate updated files on Cloudfront


  
