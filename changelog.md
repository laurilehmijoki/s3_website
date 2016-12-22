# Changelog

This project uses [Semantic Versioning](http://semver.org).

## 3.0.0

The CloudFront client now uses the official AWS SDK. If your `s3_website.yml`
file contains the `cloudfront_distribution_config` setting, you might need to modify
the it. The modifications should be rather straightforward, just follow the
[instructions](https://github.com/laurilehmijoki/configure-s3-website/blob/master/changelog.md#200).

In other words, if you don't use CloudFront, upgrading to 3.0.0 should not
result in any problems.

## 2.16.0

* Add command `s3_website install`

## 2.15.1

* Support all AWS regions

## 2.15.0

* Add new setting `content_type`
 
  See <https://github.com/laurilehmijoki/s3_website/issues/232> for discussion

## 2.14.3

* Fix mime type of an already-gzipped .json file
  
  See <https://github.com/laurilehmijoki/s3_website/pull/231>

## 2.14.2

* Apply correct mime type on already-gzipped files

  See <https://github.com/laurilehmijoki/s3_website/issues/229#issuecomment-237229421> for discussion  

## 2.14.1

* Do not gzip a file that is already gzipped

  See <https://github.com/laurilehmijoki/s3_website/issues/229> for discussion

## 2.14.0

* Add support for CloudFront wildcard invalidations

  Introduced a new setting, `cloudfront_wildcard_invalidation: (true|false)`

## 2.13.0

* Add support for `dnf`, a Linux package manager

## 2.12.3

* Fix <https://github.com/laurilehmijoki/s3_website/issues/208>

## 2.12.2

* Merge <https://github.com/laurilehmijoki/s3_website/pull/190>

## 2.12.1

* Fix broken build

## 2.12.0

* Automatically detect Middleman generated websites

## 2.11.2

* A new fix based on
  <https://github.com/laurilehmijoki/s3_website/issues/181#issuecomment-141397992>

## 2.11.1

* Prevent runaway recursion in file listing

  See <https://github.com/laurilehmijoki/s3_website/issues/181> for discussion

## 2.11.0

* Add the `s3_key_prefix` setting

## 2.10.0

* Support glob hashes in `cache_control`

## 2.9.0

* Add setting `cache_control`

## 2.8.6

* Detect changed file even though the file has the same contents with another file on the S3 bucket

  See <https://github.com/laurilehmijoki/s3_website/issues/156> for discussion.

## 2.8.5

* URL encode (ä|ö|ü) in invalidation path

## 2.8.4

* URL encode ' in invalidation path

  See <https://github.com/laurilehmijoki/s3_website/issues/63> for discussion.

* Accept ' in `exclude_from_upload` and `ignore_on_server`

## 2.8.3

* Fix bug where the setting `cloudfront_invalidate_root: true` resulted in a
  CloudFront invalidation even if there were no changes to push.

  See <https://github.com/laurilehmijoki/s3_website/issues/149> for discussion.

## 2.8.1

* Change CloudFront `OriginProtocolPolicy` to `http-only`

  See <https://github.com/laurilehmijoki/s3_website/issues/152> for discussion.

## 2.8.0

* Automatically gzip `.ico` files, if `gzip: true`

## 2.7.6

* Null-check the result of File.listFiles

  See <https://github.com/laurilehmijoki/s3_website/issues/145> for discussion.

## 2.7.5

* Remove superfluous dot from error message

## 2.7.4

* Show a helpful error message if the configured site is missing

  See <https://github.com/laurilehmijoki/s3_website/issues/144> for discussion.

## 2.7.3

* Support valid URI characters in max_age glob

  See <https://github.com/laurilehmijoki/s3_website/issues/140> for discussion.

## 2.7.2

* Fix Windows issue

  See <https://github.com/laurilehmijoki/s3_website/issues/105> for discussion.

## 2.7.1

* Loosen dependency requirements

  See <https://github.com/laurilehmijoki/s3_website/pull/135> for discussion.

## 2.7.0

* Add setting `treat_zero_length_objects_as_redirects`

  Before, `s3_website push` always uploaded one zero-length object for each
  configured redirect. From now on, you can instruct s3\_website to treat
  zero-length S3 objects as existing redirects. If you have a large amount of
  redirects on your site, you may find that this feature decreases the duration
  of `s3_website push`.

  See <https://github.com/laurilehmijoki/s3_website/issues/132> for discussion.

## 2.6.1

* Always invalidate the object */index.html* if the setting `cloudfront_invalidate_root` is on

  See https://github.com/laurilehmijoki/s3_website/pull/130 for discussion

## 2.6.0

* Support `--config-dir` in `cfg apply`

## 2.5.1

* Print **Would have updated|redirected|created** when running with `--dry-run`

## 2.5.0

* Add `push --force` option

  When the user pushes with force, s3_website skips the diff. This is helpful for the
  users who wish to update the S3 object metadata.

## 2.4.0

* Add `ignore_on_server: _DELETE_NOTHING_ON_THE_S3_BUCKET_` for the sake of convenience

  See https://github.com/laurilehmijoki/s3_website/issues/121 for discussion.

## 2.3.1

* Add Windows support

## 2.3.0

* The command `s3_website cfg apply` now supports `--headless` and
  `--autocreate-cloudfront-dist`

## 2.2.0

* Specify the location of the website in the *s3_website.yml* file

  Just add the setting `site: path-to-your-website` into the file.

* Fix Nanoc auto detection

  Previously, a website in *public/output* was not automatically detected.

## 2.1.16

* Support non-US-ASCII files when using `ignore_on_server`

  Fixes https://github.com/laurilehmijoki/s3_website/issues/102

## 2.1.15

* Support non-US-ASCII files when using `max_age`

  Fixes https://github.com/laurilehmijoki/s3_website/issues/102

## 2.1.14

* Hide false AWS SDK alarms

  Fixes https://github.com/laurilehmijoki/s3_website/issues/104

## 2.1.13

* Print JVM stack trace on error and `--verbose`

  This eases debugging

## 2.1.12

* Exit with status 1 when encountering an unrecognised CLI option

  Fixes https://github.com/laurilehmijoki/s3_website/issues/103

## 2.1.11

* Fix documentation for the `--config-dir` option

  The `--config_dir` option has changed to `--config-dir` in version 2 of this
  gem.

## 2.1.10

* Remove warning on Ruby 1.8

## 2.1.9

* Separate development and production code more clearly in the s3_website
  executable

## 2.1.8

* Remove unused code in the s3_website executable

## 2.1.7

* Remove local db

  It turned out to be too complex to maintain

## 2.1.6

* Automatically add slash to redirects if needed

## 2.1.5

* Target JVM 1.6 in build.sbt

## 2.1.4

* Fix reason-for-upload message

## 2.1.3

* Fix boolean logic in reason-for-upload

## 2.1.2

* Show a more informative message if the jar file is corrupted.

## 2.1.1

* Verify that the s3_website.jar is not corrupted. Download it again, if it is.

## 2.1.0

* Show the upload reason when calling `push --verbose`

## 2.0.1

* Rename binary s3_website_monadic to s3_website

## 2.0.0

### New features

* Faster uploads for extra large sites

   Use proper multithreading with JVM threads.

* Simulate deployments with `push --dry-run`

* Support CloudFront invalidations when the site contains over 3000 files

* Display transferred bytes

* Display upload speed

* `push --verbose` switch

### Bug fixes

* Fault tolerance – do not crash if one of the uploads fails

   Before, the push command crashed if something unexpected happened. From now
   on, s3_website will run all the operations it can, and report errors in the
   end.

### Java is now required

* The `push` command is now written in Scala

   This means that you need Java 1.6 or above to run the command `s3_website
   push`.

### Removed features

* `extensionless_mime_type`

    s3_website now relies on Apache Tika to infer the mime type.

* the `--headless` switch on the command-line

    s3_website always deletes the files that are on the S3 bucket but not on the local file system.
    Use the settings `ignore_on_server` and `exclude_from_upload` to control the retained files.

* You can no longer use this gem as a Ruby library. You can migrate by calling
  the `s3_website push --site=x --config-dir=y` system command from your Ruby code.

* `gzip_zopfli: true`

    At the time of writing, there does not exist a stable zopfli implementation
    for Java.

### Other backward incompatible changes

* The option `--config_dir` has changed to `--config-dir`

## 1.8.1

* Do not push the *.env* file

## 1.8.0

* Add support for (dotenv)[https://github.com/bkeepers/dotenv]

## 1.7.6

* Remove a test setting from *Gemfile*

## 1.7.5

* Improve significantly the performance of the push command.

    See <https://github.com/laurilehmijoki/s3_website/pull/88> for more info.

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
