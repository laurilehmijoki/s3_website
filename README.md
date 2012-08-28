# jekyll-s3

[![Build
Status](https://secure.travis-ci.org/laurilehmijoki/jekyll-s3.png)]
(http://travis-ci.org/laurilehmijoki/jekyll-s3)


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
cloudfront_distribution_id: YOUR_CLOUDFRONT_DIST_ID (OPTIONAL)
</pre>

  * Edit it with your details.

## Deploy!

  * Run `jekyll-s3`. Done.

## Want the root url to render index.html?

  * Log into <https://console.aws.amazon.com/s3/home>
  * Set the Index document to index.html in Bucket Properties >
    Website.
  * Visit the website endpoint:
    (http://yourblog.s3-website...amazonaws.com)

## How to use Cloudfront to deliver your blog

It is easy to deliver your S3-based web site via Cloudfront, the CDN of Amazon.

  * Go to <https://console.aws.amazon.com/cloudfront/home>
  * Create a distribution and set the your Jekyll S3 bucket as the origin
  * Add the `cloudfront_distribution_id: your-dist-id` setting into
    `_jekyll_s3.yml`
  * Run `jekyll-s3` to deploy your site to S3 and invalidate the Cloudfront
    distribution

## Todo

  * Upload new / updated files *only* (using s3-sync?)

## Development

  * Install bundler and run `bundle install`
  * Run the integration tests by running `bundle exec cucumber`
  * Run the unit tests by running `bundle exec rspec spec/lib/*.rb`

## License

MIT

## Copyright

Copyright (c) 2011 VersaPay, Philippe Creux.

