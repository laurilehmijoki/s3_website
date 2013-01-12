# jekyll-s3

[![Build Status](https://secure.travis-ci.org/laurilehmijoki/jekyll-s3.png)] (http://travis-ci.org/laurilehmijoki/jekyll-s3)
[![Gem Version](https://fury-badge.herokuapp.com/rb/jekyll-s3.png)](http://badge.fury.io/rb/jekyll-s3)

Deploy your jekyll site to S3.

## What jekyll-s3 can do for you

* Upload your site to AWS S3
* Help you use AWS Cloudfront to distribute your Jekyll blog
* Create an S3 website for you

## Install

    gem install jekyll-s3

## Usage

* Go to your jekyll site directory
* Run `jekyll-s3`. It generates a configuration file called `_jekyll_s3.yml` that looks like this:
<pre>
s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
s3_bucket: your.blog.bucket.com
s3_endpoint: YOUR_ENDPOINT
</pre>
* Edit it with your details (you can use [ERB](http://ruby-doc.org/stdlib-1.9.3/libdoc/erb/rdoc/ERB.html) in the file)
* Run `configure-s3-website --config-file _jekyll_s3.yml` This will configure
  your bucket to function as an S3 website. If the bucket does not exist,
  `configure-s3-website` will create it for you.

* Run `jekyll-s3` to push your Jekyll blog to S3. Congratulations! You are live.

## Additional features

### Reduced Redundancy

You can reduce the cost of hosting your blog on S3 by using Reduced Redundancy Storage:

  * In `_jekyll_s3.yml`, set `s3_reduced_redundancy: true`
  * All objects uploaded after this change will use the Reduced Redundancy Storage.
  * If you want to change all of the files in the bucket, you can change them through the AWS console, or update the timestamp on the files before running `jekyll-s3` again

### How to use Cloudfront to deliver your blog

It is easy to deliver your S3-based web site via Cloudfront, the CDN of Amazon.

  * Go to <https://console.aws.amazon.com/cloudfront/home>
  * Create a distribution and set the your Jekyll S3 bucket as the origin
  * Add the `cloudfront_distribution_id: your-dist-id` setting into
    `_jekyll_s3.yml`
  * Run `jekyll-s3` to deploy your site to S3 and invalidate the Cloudfront
    distribution

### The headless mode

Jekyll-s3 has a headless mode, where human interactions are disabled.

In the headless mode, `jekyll-s3` will automatically delete the files on the S3
bucket that are not on your local computer.

Enable the headless mode by adding the `--headless` or `-h` argument after
`jekyll-s3`.

## Known issues

### Only S3 buckets in the US Standard region work

Jekyll-s3 supports only S3 buckets that are in the US Standard region. If your
bucket is currently on some other region, you can set a non-existing bucket in
`_jekyll_s3.yml` and then run `configure-s3-website --config-file
_jekyll_s3.yml`. This creates you a bucket in the US Standard region.

## Development

### Versioning

Jekyll-s3 uses [Semantic Versioning](http://semver.org).

### Tests

  * Install bundler and run `bundle install`
  * Run all tests by invoking `rake test`
  * Run the integration tests by running `bundle exec cucumber`
  * Run the unit tests by running `bundle exec rspec spec/lib/*.rb`

### Contributing

We (users and developers of Jekyll-s3) welcome patches, pull requests and
ideas for improvement.

When sending pull requests, please accompany them with tests. Favor BDD style
in test descriptions. Use VCR-backed integration tests where possible. For
reference, you can look at the existing Jekyll-s3 tests.

If you are not sure how to test your pull request, you can ask the main
developer (currently Lauri Lehmijoki) to supplement the request with tests.
However, by including proper tests, you increase the chances of your pull
request being incorporated into future releases.

## License

MIT

## Copyright

Copyright (c) 2011 VersaPay, Philippe Creux.

Contributors (in alphabetical order)
* Cory Kaufman-Schofield
* Chris Kelly
* Chris Moos
* Lauri Lehmijoki
* Mason Turner
* stanislas
