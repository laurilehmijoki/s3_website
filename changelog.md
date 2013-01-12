# Changelog

## 2.2.4

* Print the web site URL instead of the bucket URL
  [#22](https://github.com/laurilehmijoki/jekyll-s3/issues/22).

## 2.2.3

* Allow bugfixes for transitive dependencies *filey-diff*,
  *simple-cloudfront-invalidator* and *configure-s3-website*, since they are
  semantically versioned

## 2.2.2

* Remove debug code that wrote the list of site files into /tmp/test.txt

## 2.2.1

* Upload also dot files
  ([#16](https://github.com/laurilehmijoki/jekyll-s3/pull/16))

## 2.2.0

* Automatically configure the S3 bucket to function as a website.

  This makes it easier to start hosting a Jekyll site on S3. It removes the need
  to manually configure the S3 bucket in the Amazon AWS console.

## 2.1.2

* Remove a superfluous comma from uploader.rb. The comma might have caused
  problems for some Ruby 1.8.7 users.

## 2.1.1

* Remove optional settings from the generated `_jekyll_s3.yml` file

## 2.1.0

* Added support for S3 reduced redundancy storage

## 2.0.0

* Set content type of the uploaded files
* (Increment major version because of the changed return value in
  Jekyll::S3::CLI::run)

## 1.0.0

* CloudFront invalidation on changed files only
* Start using [Semantic versioning](http://semver.org/)

## 0.0.7

* Headless mode

## 0.0.6

* Upload only new or changed files
* Support ERB syntax in `_jekyll_s3.yml`

## 0.0.5

* Invalidate the Cloudfront distribution of the Jekyll S3 bucket.
