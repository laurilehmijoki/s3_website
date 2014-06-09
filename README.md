# Deploy your website to S3

[![Build Status](https://travis-ci.org/laurilehmijoki/s3_website.png?branch=master)](https://travis-ci.org/laurilehmijoki/s3_website)
[![Gem Version](https://fury-badge.herokuapp.com/rb/s3_website.png)](http://badge.fury.io/rb/s3_website)

## What `s3_website` can do for you

* Create and configure an S3 website for you
* Upload your static website to AWS S3
 * Jekyll and Nanoc are automatically supported
* Help you use AWS Cloudfront to distribute your website
* Improve page speed with HTTP cache control and gzipping
* Set HTTP redirects for your website
* (for other features, see the documentation below)

## Install

    gem install s3_website

s3_website needs both [Ruby](https://www.ruby-lang.org/en/downloads/)
and [Java](http://java.com) to run. (S3_website is partly written in Scala, hence the need for Java.)

## Usage

Here's how you can get started:

* Create API credentials that have sufficient permissions to S3. More info
  [here](additional-docs/setting-up-aws-credentials.md).
* Go to your website directory
* Run `s3_website cfg create`. This generates a configuration file called `s3_website.yml`.
* Put your AWS credentials and the S3 bucket name into the file
* Run `s3_website cfg apply`. This will configure your bucket to function as an
  S3 website. If the bucket does not exist, the command will create it for you.
* Run `s3_website push` to push your website to S3. Congratulations! You are live.

### For Jekyll users

S3_website will automatically discover your website in the *_site* directory.

### For Nanoc users

S3_website will automatically discover your website in the *public/output* directory.

### For others

It's a good idea to store the `s3_website.yml` file in your project's root.
Let's say the contents you wish to upload to your S3 website bucket are in
*my_website_output*. You can upload the contents of that directory with
`s3_website push --site my_website_output`.

If you want to store the `s3_website.yml` file in a directory other than
the project's root you can specify the directory.
`s3_website push --config_dir config`.

### Using environment variables

You can use ERB in your `s3_website.yml` file which incorporates environment variables:

```yaml
s3_id: <%= ENV['S3_ID'] %>
s3_secret: <%= ENV['S3_SECRET'] %>
s3_bucket: blog.example.com
```

(If you are using `s3_website` on an [EC2 instance with IAM
roles](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/UsingIAM.html#UsingIAMrolesWithAmazonEC2Instances),
you can omit the `s3_id` and `s3_secret` keys in the config file.)

S3_website implements supports for reading environment variables from a file using
the [dotenv](https://github.com/bkeepers/dotenv) gem. You can create a `.env` file
in the project's root directory to take advantage of this feature. Please have
a look at [dotenv's usage guide](https://github.com/bkeepers/dotenv#usage) for
syntax information.

## Project goals

* Provide a command-line interface tool for deploying and managing S3 websites
* Let the user have all the S3 website configurations in a file
* Minimise or remove the need to use the AWS Console
* Allow the user to deliver the website via CloudFront
* Automatically detect the most common static website tools, such as Jekyll or
  Nanoc
* Be simple to use: require only the S3 credentials and the name of the S3
  bucket
* Let the power users benefit from advanced S3 website features such as
  redirects, Cache-Control headers and gzip support
* Be as fast as possible. Do in parallel all that can be done in parallel.

`s3_website` attempts to be a command-line interface tool that is easy to
understand and use. For example, `s3_website --help` should print you all the
things it can perform. Please create an issue if you think the tool is
incomprehensible or inconsistent.

## Additional features

### Cache Control

You can use the `max_age` configuration option to enable more effective browser
caching of your static assets. There are two possible ways to use the option:
you can specify a single age (in seconds) like so:

```yaml
max_age: 300
```

Or you can specify a hash of globs, and all files matching those globs will have
the specified age:

```yaml
max_age:
  "assets/*": 6000
  "*": 300
```

Place the configuration into the file `s3_website.yml`.

### Gzip Compression

If you choose, you can use compress certain file types before uploading them to
S3. This is a recommended practice for maximizing page speed and minimizing
bandwidth usage.

To enable Gzip compression, simply add a `gzip` option to your `s3_website.yml`
configuration file:

```yaml
gzip: true
```

Note that you can additionally specify the file extensions you want to Gzip
(`.html`, `.css`, `.js`, and `.txt` will be compressed when `gzip: true`):

```yaml
gzip:
  - .html
  - .css
  - .md
```

Remember that the extensions here are referring to the *compiled* extensions,
not the pre-processed extensions.

### Using non-standard AWS regions

By default, `s3_website` uses the US Standard Region. You can upload your
website to other regions by adding the setting `s3_endpoint` into the
`s3_website.yml` file.

For example, the following line in `s3_website.yml` will instruct `s3_website` to
push your site into the Tokyo region:

```yaml
s3_endpoint: ap-northeast-1
```

The valid `s3_endpoint` values consist of the [S3 location constraint
values](http://docs.amazonwebservices.com/general/latest/gr/rande.html#s3_region).

### Ignoring files you want to keep on AWS

Sometimes there are files or directories you want to keep on S3, but not on
your local machine. You may define a regular expression to ignore files like so:

```yaml
ignore_on_server: that_folder_of_stuff_i_dont_keep_locally
```

You may also specify the values as a list:

```yaml
ignore_on_server:
  - that_folder_of_stuff_i_dont_keep_locally
  - file_managed_by_somebody_else
```

### Excluding files from upload

You can instruct `s3_website` not to push certain files:

```yaml
exclude_from_upload: test
```

The value can be a regex, and you can specify many of them:

```yaml
exclude_from_upload:
  - test
  - (draft|secret)
```

### Reduced Redundancy

You can reduce the cost of hosting your blog on S3 by using Reduced Redundancy Storage:

  * In `s3_website.yml`, set `s3_reduced_redundancy: true`
  * All objects uploaded after this change will use the Reduced Redundancy Storage.
  * If you want to change all of the files in the bucket, you can change them through the AWS console, or update the timestamp on the files before running `s3_website` again

### How to use Cloudfront to deliver your blog

It is easy to deliver your S3-based web site via Cloudfront, the CDN of Amazon.

#### Creating a new CloudFront distribution

When you run the command `s3_website cfg apply`, it will ask you whether you
want to deliver your website via CloudFront. If you answer yes, the command will
create a CloudFront distribution for you.

#### Using your existing CloudFront distribution

If you already have a CloudFront distribution that serves data from your website
S3 bucket, just add the following line into the file `s3_website.yml`:

    cloudfront_distribution_id: your-dist-id

Next time you run `s3_website push`, it will invalidate the items on CloudFront and
thus force the CDN system to reload the changes from your website S3 bucket.

#### Specifying custom settings for your CloudFront distribution

`s3_website` lets you define custom settings for your CloudFront distribution.

For example, like this you can define a your own TTL and CNAME:

```yaml
cloudfront_distribution_config:
  default_cache_behavior:
    min_TTL: <%= 60 * 60 * 24 %>
  aliases:
    quantity: 1
    items:
      CNAME: your.website.com
```

Once you've saved the configuration into `s3_website.yml`, you can apply them by
running `s3_website cfg apply`.

#### Invalidating root resources instead of index.htmls

By default, `s3_website push` calls the CloudFront invalidation API with the
file-name-as-it-is. This means that if your file is *article/index.html*, the
push command will call the invalidation API on the resource
*article/index.html*.

You can instruct the push command to invalidate the root resource instead of the
*index.html* resource by adding the following setting into the configuration
file:

    cloudfront_invalidate_root: true

To recap, this setting instructs s3_website to invalidate the root resource
(e.g., *article/*) instead of the filename'd resource (e.g.,
*article/index.html*).

No more index.htmls in your URLs!

*Note*: If the root resource on your folder displays an error instead of the
index file, your source bucket in Cloudfront likely is pointing to the S3 Origin,
*example.com.s3.amazonaws.com*. Update the source to the S3 Website Endpoint,
*e.g. example.com.s3-website-us-east-1.amazonaws.com*, to fix this.

### Configuring redirects on your S3 website

You can set HTTP redirects on your S3 website in two ways. If you only need
simple "301 Moved Premanently" redirects for certain keys, use the Simple
Redirects method. Otherwise, use the Routing Rules method.

#### Simple Redirects

For simple redirects `s3_website` uses Amazon S3's
[`x-amz-website-redirect-location`](http://docs.aws.amazon.com/AmazonS3/latest/dev/how-to-page-redirect.html)
metadata. It will create zero-byte objects for each path you want
redirected with the appropriate `x-amz-website-redirect-location` value.

For setting up simple redirect rules, simply list each path and target
as key-value pairs under the `redirects` configuration option:

```yaml
redirects:
  index.php: /
  about.php: /about.html
  music-files/promo.mp4: http://www.youtube.com/watch?v=dQw4w9WgXcQ
```

#### Routing Rules

You can configure more complex redirect rules by adding the following
configuration into the `s3_website.yml` file:

```yaml
routing_rules:
  - condition:
      key_prefix_equals: blog/some_path
    redirect:
      host_name: blog.example.com
      replace_key_prefix_with: some_new_path/
      http_redirect_code: 301
```

After adding the configuration, run the command `s3_website cfg apply` on your
command-line interface. This will apply the routing rules on your S3 bucket.

For more information on configuring redirects, see the documentation of the
[configure-s3-website](https://github.com/laurilehmijoki/configure-s3-website#configuring-redirects)
gem, which comes as a transitive dependency of the `s3_website` gem. (The
command `s3_website cfg apply` internally calls the `configure-s3-website` gem.)

### Specifying custom concurrency level

By default, `s3_website` does 3 operations in parallel. An operation can be an
HTTP PUT operation against the S3 API, for example.

You can increase the concurrency level by adding the following setting into the
`s3_website.yml` file:

```
concurrency_level: <integer>
```

However, because S3 throttles connections, there's an upper limit to the
level of parallelism. If you start to see end-of-file errors, decrease the
concurrency level. Conversely, if you don't experience any errors, you can
increase the concurrency level and thus benefit from faster uploads.

If you experience the "too many open files" error, either increase the amount of
maximum open files (on Unix-like systems, see `man ulimit`) or decrease the
`concurrency_level` setting.

### Simulating deployments

You can simulate the `s3_website push` operation by adding the
`--dry-run` switch. The dry run mode will not apply any modifications on your S3
bucket or CloudFront distribution. It will merely print out what the `push`
operation would actually do if run without the dry switch.

You can use the dry run mode if you are unsure what kind of effects the `push`
operation would cause to your live website.

## Migrating from v1 to v2

Please read the [release note](/changelog.md#200) on version 2. It contains
information on backward incompatible changes.

You can find the v1 branch
[here](https://github.com/laurilehmijoki/s3_website/tree/1.x). It's in
maintenance mode. This means that v1 will see only critical bugfix releases.

## Example configurations

See [example-configurations](additional-docs/example-configurations.md).

## On security

If the source code of your website is publicly
available, ensure that the `s3_website.yml` file is in the list of ignored files.
For git users this means that the file `.gitignore` should mention the
`s3_website.yml` file.

If you use the .dotenv gem, ensure that you do not push the `.env` file to a
public git repository.

## Known issues

Please create an issue and send a pull request if you spot any.

## Versioning

s3_website uses [Semantic Versioning](http://semver.org).

In the spirit of semantic versioning, here is the definition of public API for
s3_website: Within a major version, s3_website will not break
backwards-compatibility of anything that is mentioned in this README file.

## Development

See [development](additional-docs/development.md).

### Contributing

We (users and developers of s3_website) welcome patches, pull requests and
ideas for improvement.

When sending pull requests, please accompany them with tests. Favor BDD style
in test descriptions. Use VCR-backed integration tests where possible. For
reference, you can look at the existing s3_website tests.

If you are not sure how to test your pull request, you can ask the [gem owners
](http://rubygems.org/gems/s3_website) to supplement the request with tests.
However, by including proper tests, you increase the chances of your pull
request being incorporated into future releases.

## License

MIT. See the LICENSE file for more information.

## Contributors

This gem is created by Lauri Lehmijoki. Without the valuable work of [Philippe
Creux](https://github.com/pcreux) on
[jekyll-s3](https://github.com/laurilehmijoki/jekyll-s3), this project would not
exist.

Contributors (in alphabetical order)
* Alan deLevie
* Cory Kaufman-Schofield
* Chris Kelly
* Chris Moos
* Christopher Petersen
* David Michael Barr
* David Raffensperger
* Douglas Teoh
* Greg Karékinian
* John Allison
* Jordan White
* László Bácsi
* Mason Turner
* Michael Bleigh
* Philip I. Thomas
* Philippe Creux
* Piotr Janik
* Shigeaki Matsumura
* stanislas
* Tate Johnson
* Toby Marsden
* Trevor Fitzgerald
* Zee Spencer

## Donations

[![Support via Gittip](https://rawgithub.com/twolfson/gittip-badge/0.2.0/dist/gittip.png)](https://www.gittip.com/laurilehmijoki/)
