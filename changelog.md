# Changelog

This project uses [Semantic Versioning](http://semver.org).

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
