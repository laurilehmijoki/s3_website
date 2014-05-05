# Changelog

This project uses [Semantic Versioning](http://semver.org).

## 2.0.0

This release contains backward breaking changes. Please read the section below
for more info.

### Java is now required

* The `push` command is now written in Scala. This means that you need Java 1.6
  or above to run the command `s3_website push`.

### Removed features

* `extensionless_mime_type`

    s3_website now relies on Apache Tika to infer the mime type.

* the `--headless` switch on the command-line

    s3_website always deletes the files that are on the S3 bucket but not on the local file system.
    Use the settings `ignore_on_server` and `exclude_from_upload` to control the retained files.

## 1.7.4

* Fix issue https://github.com/laurilehmijoki/s3_website/issues/83

## 1.7.3

* Fix Digest::Digest deprecation warn on Ruby 2.1.0

  This warning did appear then one used the `cfg create` or `cfg apply`
  commands.

## 1.7.2

* This release contains no code changes (the indended change is in the 1.7.3
  release)

## 1.7.1

* Do not override ERB when adding CloudFront dist

## 1.7.0

* Add zopfli compression support

## 1.6.13

* Depend on any 1-series version of the aws-sdk gem

## 1.6.12

* Fix bug <https://github.com/laurilehmijoki/s3_website/issues/63>

## 1.6.11

* Loosen the dependency spec of mime-types (#70)

## 1.6.10

* Fix bug <https://github.com/laurilehmijoki/s3_website/issues/38>

## 1.6.9

* Fix Digest::Digest deprecation warn on Ruby 2.1.0

## 1.6.8

* Fix content-type problem
  (<https://github.com/laurilehmijoki/s3_website/pull/66>)

## 1.6.7

* Support the eu-west-1 location constraint for the commands `cfg apply` and
  `cfg create`

## 1.6.6

* Mark all text documents as UTF-8 encoded

## 1.6.5

* In case of error, exit with status 1

## 1.6.4

* Add systematic error handling

  Fixes issue https://github.com/laurilehmijoki/s3_website/issues/52.

## 1.6.3

* Invalidate a deleted file on CloudFront

## 1.6.2

* Fix issue <https://github.com/laurilehmijoki/s3_website/pull/54>

## 1.6.1

* Fix issue <https://github.com/laurilehmijoki/s3_website/issues/30>

## 1.6.0

* Add support for excluding files from upload
 * s3_website.yml now supports `exclude_from_upload`
* Support multiple values on the `ignore_on_server` setting

## 1.5.0

* Add support for specifying the MIME type for extensionless files

## 1.4.5

* If max-age=0, set `Cache-Control: no-cache, max-age=0`

## 1.4.4

* Add support for eu-west-1 as a location constraint

## 1.4.3

* Decrease the default concurrency level to 3

  See https://github.com/laurilehmijoki/s3_website/issues/8#issuecomment-24855991
  for discussion.

## 1.4.2

* Fix `s3_website cfg apply` for CloudFront setup (#33)

## 1.4.1

* Fix diff for Windows users

  See
  <https://github.com/laurilehmijoki/s3_website/issues/8#issuecomment-23683568>
  for discussion.

## 1.4.0

* Add setting `cloudfront_invalidate_root`

## 1.3.2

* Move blacklist filtering into a better place

## 1.3.1

* Print to stdout the initial state of the diff progress indicator

## 1.3.0

* Show a progress indicator when calculating diff

## 1.2.1

* Use `print` instead of `puts` when printing to stdout in a concurrent context

## 1.2.0

* Use the `--config_dir` CLI option to specify the directory from where to read
  the `s3_website.yml` config file

## 1.1.2

* Mention the MIT license in the gemspec file

## 1.1.1

* Mention the new `concurrency_level` setting in the sample config file

## 1.1.0

* Add possibility to define the concurrency level in *s3_website.yml*

## 1.0.3

* Reject blacklisted files in a more appropriate place

## 1.0.2

* Never upload the file *s3_website.yml*

## 1.0.1

* Set default concurrency level to 100. Related to issue [#6](https://github.com/laurilehmijoki/s3_website/issues/6).

## 1.0.0

* Make 0.4.0 the version 1.0.0

## 0.4.0

* Include the available configs in the sample s3_website.yml file

## 0.3.0

* Add Nanoc support

## 0.2.1

* Remove Gemfile.lock
* Rename gemspec file

## 0.2.0

* Add command `s3_website cfg apply`

## 0.1.0

* First version
