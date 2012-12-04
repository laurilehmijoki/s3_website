# jekyll-s3

[![Build
Status](https://secure.travis-ci.org/laurilehmijoki/jekyll-s3.png)]
(http://travis-ci.org/laurilehmijoki/jekyll-s3)

Deploy your jekyll site to S3.

## What jekyll-s3 can do for you

* Upload your site to AWS S3
* Help you use AWS Cloudfront to distribute your Jekyll blog
* Create the S3 bucket for you

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
  * Hint: you can use ERB syntax in `_jekyll_s3.yml`. This is handy, if you
    want to store the AWS credentials as environmental variables.

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

## The headless mode

Jekyll-s3 has a headless mode, in which the interactions with a user are
disabled.

In the headless mode, Jekyll-s3 will automatically delete the files on the S3
bucket that are not on your local computer. (You can use the delete feature to
unpublish blog posts.)

Enable the headless mode by adding the `--headless` or `-h` argument after
`jekyll-s3`.

## Changelog

### 1.0.0

* CloudFront invalidation on changed files only
* Start using [Semantic versioning](http://semver.org/)

### 0.0.7

* Headless mode

### 0.0.6

* Upload only new or changed files
* Support ERB syntax in `_jekyll_s3.yml`

### 0.0.5

* Invalidate the Cloudfront distribution of the Jekyll S3 bucket.

## Known issues

### Only S3 buckets in the US Standard region work

Jekyll-s3 supports only S3 buckets that are in the US Standard region. If your
bucket is currently on some other region, you can set a non-existing
bucket in *_jekyll_s3.yml* and then run `jekyll-s3`. Jekyll-s3 will then create
the bucket in the US Standard region.

## Development

  * Install bundler and run `bundle install`
  * Run the integration tests by running `bundle exec cucumber`
  * Run the unit tests by running `bundle exec rspec spec/lib/*.rb`

## License

MIT

## Copyright

Copyright (c) 2011 VersaPay, Philippe Creux.

Contributors (in alphabetical order)
* Cory Kaufman-Schofield
* Chris Kelly
* Lauri Lehmijoki
