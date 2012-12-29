# Changelog

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
