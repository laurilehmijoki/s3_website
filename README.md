# Deploy your website to S3

[![Build Status](https://travis-ci.org/laurilehmijoki/s3_website.png?branch=master)](https://travis-ci.org/laurilehmijoki/s3_website)
[![Gem Version](https://fury-badge.herokuapp.com/rb/s3_website.png)](http://badge.fury.io/rb/s3_website)

## What `s3_website` can do for you

* Create and configure an S3 website for you
* Upload your static website to AWS S3
* [Jekyll](http://jekyllrb.com/), [Nanoc](http://nanoc.ws/), and [Middleman](https://middlemanapp.com/) are automatically supported
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
  [here](https://github.com/laurilehmijoki/s3_website/tree/master/additional-docs/setting-up-aws-credentials.md).
* Go to your website directory
* Run `s3_website cfg create`. This generates a configuration file called `s3_website.yml`.
* Put your AWS credentials and the S3 bucket name into the file
* Run `s3_website cfg apply`. This will configure your bucket to function as an
  S3 website. If the bucket does not exist, the command will create it for you.
* Run `s3_website push` to push your website to S3. Congratulations! You are live.
* At any later time when you would like to synchronise your local website with
  the S3 website, simply run `s3_website push` again.
  (It will calculate the difference, update the changed files,
  upload the new files and delete the obsolete files.)

### Specifying the location of your website

S3_website will automatically discover websites in the *_site* and
*public/output* directories.

If your website is not in either of those directories, you can
point the location of your website in two ways:

1. Add the line `site: path-to-your-website` into the `s3_website.yml` file
2. Or, use the `--site=path-to-your-site` command-line argument

If you want to store the `s3_website.yml` file in a directory other than
the project's root you can specify the directory like so:
`s3_website push --config-dir config`.

### Using standard AWS credentials

If you omit `s3_id` from your `s3_website.yml`, S3_website will fall back to reading from the [default AWS SDK locations](http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html). For instance, if you've used `aws configure` to set up credentials in `~/.aws/credentials`, S3_website can use these.

### Using an AWS profile or a profile that assumes a role

If you omit `s3_id`, `s3_secret`, and `session_token` you can specify an AWS credentials profile to use via the `profile` configuration variable, eg:

    profile: name_of_aws_profile

In addition, if you want this profile to assume a role before executing against S3, use the `profile_assume_role_arn` variable, eg:

    profile_assume_role_arn: arn_of_role_to_assume

(Note: you have to use a regular profile with an ID and SECRET and specify the role ARN via a variable like this instead of a profile that specifies a `role_arn` as documented [here](http://docs.aws.amazon.com/cli/latest/userguide/cli-roles.html) since it does not look like the Java SDK supports that format, yet...)

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

S3_website implements support for reading environment variables from a file using
the [dotenv](https://github.com/bkeepers/dotenv) gem. You can create a `.env` file
in the project's root directory to take advantage of this feature. Please have
a look at [dotenv's usage guide](https://github.com/bkeepers/dotenv#usage) for
syntax information.

Your `.env` file should containing the following variables:

    S3_ID=FOO
    S3_SECRET=BAR

S3_website will also honor environment variables named `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and `AWS_SESSION_TOKEN` (if using STS) automatically if `s3_id` is ommitted from `s3_website.yml`.

## Project goals

* Provide a command-line interface tool for deploying and managing S3 websites
* Let the user have all the S3 website configurations in a file
* Minimise or remove the need to use the AWS Console
* Allow the user to deliver the website via CloudFront
* Automatically detect the most common static website tools, such as [Jekyll](http://jekyllrb.com/), [Nanoc](http://nanoc.ws/), and [Middleman](https://middlemanapp.com/).
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

You can use either the setting `max_age` or `cache_control`to enable more
effective browser caching of your static assets.

#### max_age

There are two possible ways to use the option:
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

After changing the `max_age` setting, push with the `--force` option.
Force-pushing allows you to update the S3 object metadata of existing files.

#### cache_control

The `cache_control` setting allows you to define an arbitrary string that s3_website
will put on all the S3 objects of your website.

Here's an example:

```yaml
cache_control: public, no-transform, max-age=1200, s-maxage=1200
```

You can also specify a hash of globs, and all files matching those globs will have
the specified cache-control string:

```yaml
cache_control:
  "assets/*": public, max-age=3600
  "*": no-cache, no-store
```

After changing the `cache_control` setting, push with the `--force` option.
Force-pushing allows you to update the S3 object metadata of existing files.

### Content type detection

By default, s3_website automatically detects the content type of a file with the help of Apache Tika.

For some file types Tika's auto detection does not work correctly. Should this problem affect you, use the `content_type`
setting to override Tika's decision:

```yaml
content_type:
  "*.myextension": application/my-custom-type
```

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
(`.html`, `.css`, `.js`, `.ico`, and `.txt` will be compressed when `gzip: true`):

```yaml
gzip:
  - .html
  - .css
  - .md
```

Remember that the extensions here are referring to the *compiled* extensions,
not the pre-processed extensions.

After changing the `gzip` setting, push with the `--force` option.

s3_website will not gzip a file that is already gzipped. This is useful in the
situations where your build tools gzip a file before you invoke `s3_website push`.

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

If you add the magic word `ignore_on_server: _DELETE_NOTHING_ON_THE_S3_BUCKET_`,
`s3_website push` will never delete any objects on the bucket.

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

After changing the `s3_reduced_redundancy` setting, push with the `--force`
option.

### How to use Cloudfront to deliver your blog

It is easy to deliver your S3-based web site via Cloudfront, the CDN of Amazon.

#### Creating a new CloudFront distribution

When you run the command `s3_website cfg apply`, it will ask you whether you
want to deliver your website via CloudFront. If you answer yes, the command will
create a CloudFront distribution for you.

If you do not want to receive this prompt, or if you are running the command in a non-interactive session, you can use `s3_website cfg apply --headless` (and optionally also use `--autocreate-cloudfront-dist` if desired).

#### Using your existing CloudFront distribution

If you already have a CloudFront distribution that serves data from your website
S3 bucket, just add the following line into the file `s3_website.yml`:

```yaml
cloudfront_distribution_id: your-dist-id
```

Next time you run `s3_website push`, it will invalidate the items on CloudFront and
thus force the CDN system to reload the changes from your website S3 bucket.

#### Specifying custom settings for your CloudFront distribution

`s3_website` lets you define custom settings for your CloudFront distribution.

For example, like this you can define your own TTL and CNAME:

```yaml
cloudfront_distribution_config:
  default_cache_behavior:
    min_ttl: <%= 60 * 60 * 24 %>
  aliases:
    quantity: 1
    items:
      CNAME: your.website.com
```

Once you've saved the configuration into `s3_website.yml`, you can apply them by
running `s3_website cfg apply`.

#### Invalidating all CloudFront resources (wildcard invalidation)

The following setting is recommended for most users:

```yaml
cloudfront_wildcard_invalidation: true
```

Over time, it can reduce your AWS bill significantly. For more information, see <http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Invalidation.html>.

#### Invalidating root resources instead of index.htmls

By default, `s3_website push` calls the CloudFront invalidation API with the
file-name-as-it-is. This means that if your file is *article/index.html*, the
push command will call the invalidation API on the resource
*article/index.html*.

You can instruct the push command to invalidate the root resource instead of the
*index.html* resource by adding the following setting into the configuration
file:

```yaml
cloudfront_invalidate_root: true
```

To recap, this setting instructs s3_website to invalidate the root resource
(e.g., *article/*) instead of the filename'd resource (e.g.,
*article/index.html*).

No more index.htmls in your URLs!

*Note*: If the root resource on your folder displays an error instead of the
index file, your source bucket in Cloudfront likely is pointing to the S3 Origin,
*example.com.s3.amazonaws.com*. Update the source to the S3 Website Endpoint,
*e.g. example.com.s3-website-us-east-1.amazonaws.com*, to fix this.

### Configuring redirects on your S3 website

You can set HTTP redirects on your S3 website in three ways. 

#### Exact page match for moving a single page
If a request is received matching a string e.g. /heated-towel-rail/ redirect to a page e.g. /  

This kind of redirect is created in the s3_website.yml file under the ```redirects:``` section as follows: 

```yaml
redirects:
  index.php: /
  about.php: /about.html
  music-files/promo.mp4: http://www.youtube.com/watch?v=dQw4w9WgXcQ
  heated-towel-rail/index.html: /
  
```

Note that the root forward slash is omitted in the requested page path and included in the redirect-to path.  Note also that in the heated-towel-rail example this also matches heated-towel-rail/ since S3 appends index.html to request URLs terminated with a slash. 

This redirect will be created as a 301 redirect from the first URL to the destination URL on the same server with the same http protocol.

Under the hood `s3_website` creates a zero-byte index.html page for each path you want redirected with the appropriate `x-amz-website-redirect-location` value in the metadata for the object. See Amazon S3's
[`x-amz-website-redirect-location`](http://docs.aws.amazon.com/AmazonS3/latest/dev/how-to-page-redirect.html)
documentation for more details.

On terminology: the left value is the redirect source and the right value is the redirect
target. For example above, *about.php* is the redirect source and */about.html* the target.

If the `s3_key_prefix` setting is defined, it will be applied to the redirect
target if and only if the redirect target points to a site-local resource and
does not start with a slash. E.g., `about.php: about.html` will be translated
into `about.php: VALUE-OF-S3_KEY_PREFIX/about.html`.

#### Prefix replacement for moving a folder of pages
Common to content migrations, content pages often move from one subdirectory to another. For example if you're moving all the case studies on your site under /portfolio/work/ to /work/. In this case we use a prefix replacement such that /portfolio/work/walkjogrun/ gets 301 redirected to /work/walkjogrun/.

To do this we add a new rule to the routing_rules: section as follows:

```
  - condition:
        key_prefix_equals: portfolio/work/
    redirect:
        protocol: https
        host_name: <%= ENV['REDIRECT_DOMAIN_NAME'] %>
        replace_key_prefix_with: work/
        http_redirect_code: 301
```

Here:

* ```-condition:``` indicates the start of a new rule. 
* ```key_prefix_equals:``` introduces the path prefix (also without the leading / per the exact page match). Note that this prefix matches anything underneath it so every case study under that path will be handled by the subsequent redirect
* ```redirect:``` indicates the start of the redirect definition 
* ```protocol:``` is optional and defaults to http.
* ```host_name:``` is optional but the default is the amazonaws.com bucket name not the actual domain name so this also effectively required for our site. In this example we use an environment variable to store the server hostname to support building to different environments. ```REDIRECT_DOMAIN_NAME``` can be configured on a CI server as well any CodePipelines responsible for building the site to different environments.  If you're running locally you'll need to set ```REDIRECT_DOMAIN_NAME=local.myhostname.com```
* ```replace_key_prefix_with:``` indicates the substitution to use in place of the matched prefix. This is the only field required by `s3_website`, so effectively this rule works like a replace rule e.g. replace portfolio/work with /work in the string portfolio/work/walkjogrun
* ```http_redirect_code:``` is optional and defaults to 302 Temporary redirect **which is terrible for SEO** since your content temporarily vanishes from the Google index until the response changes for the URL. This is almost never what you want. You *can* use this to temporarily redirect any content you haven't migrated to the new site yet as long as you remove or replace the 302 with a link to a permanent home. This tells Google to forget the old location of the page and use the new content at the new URL. For pages that move you'll see little if any discrepancy in Google traffic. 

After adding the configuration, run the command `s3_website cfg apply` on your
command-line interface. This will apply the routing rules on your S3 bucket.

For more information on configuring redirects, see the documentation of the
[configure-s3-website](https://github.com/laurilehmijoki/configure-s3-website#configuring-redirects)
gem, which comes as a transitive dependency of the `s3_website` gem. (The
command `s3_website cfg apply` internally calls the `configure-s3-website` gem.)

#### Prefix coallescing for deleting pages (or consolidating)
If you 301 redirect lots of content into one new path you're telling Google that the old pages are gone so only the destination page is important moving forward. E.g. if you had 10 services pages and consolidate them into 1 services listing page you'll lose the 10 pages uniquely optimized for different sets of keywords and retain just 1 page with no real keyword focus and hence less SEO value.

For example, we're not porting the entire set of pages under the folder /experience to the new website. Some of these pages still get traffic from either Google or inbound links so we don't want to just show a 404 content not found error. We will 301 redirect them to the most useful replacement page. In the case of /experience we don't have anything better to show than just the home page so that is how the redirect is configured.

Here's how to redirect to indicate a deleted page:

```
  - condition:
        key_prefix_equals: experience/
    redirect:
        protocol: https
        host_name: <%= ENV['REDIRECT_DOMAIN_NAME'] %>
        **replace_key_with**: /
        http_redirect_code: 301
```

Note the only difference is that instead of using ```replace_key_prefix_with``` we use ```replace_key_with``` to effectively say "replace the entire path matching the prefix specfied in the condition with the new path".

#### On skipping application of redirects

If your website has a lot of redirects, you may find the following setting
helpful:

```yaml
treat_zero_length_objects_as_redirects: true
```

The setting allows `s3_website push` to infer whether a redirect exists on the S3 bucket.
You will experience faster `push` performance when this setting is `true`.

If this setting is enabled and you modify the `redirects` setting in
*s3_website.yml*, use `push --force` to apply the modified values.

For backward-compatibility reasons, this setting is `false` by default.

In this context, the word *object* refers to object on S3, not file-system file.

### Specifying custom concurrency level

By default, `s3_website` does 3 operations in parallel. An operation can be an
HTTP PUT operation against the S3 API, for example.

You can increase the concurrency level by adding the following setting into the
`s3_website.yml` file:

```yaml
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

### S3 website in a subdirectory of the bucket

If your S3 website shares the same S3 bucket with other applications, you can
push your website into a "subdirectory" on the bucket.

Define the subdirectory like so:

```yaml
s3_key_prefix: your-subdirectory
```

### Temporary security credentials with Session Token

[AWS temporary security credentials](http://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp.html) (eg: when [assuming IAM roles](http://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html))

Usage: 

```yaml
session_token: your-token
```

## Migrating from v1 to v2

Please read the [release note](/changelog.md#200) on version 2. It contains
information on backward incompatible changes.

You can find the v1 branch
[here](https://github.com/laurilehmijoki/s3_website/tree/1.x). It's in
maintenance mode. This means that v1 will see only critical bugfix releases.

## Example configurations

- [Minimal configuration](additional-docs/example-configurations.md#minimal)
- [CloudFront optimisation](additional-docs/example-configurations.md#optimised-for-speed)
- [CloudFront multiple CNAMEs](additional-docs/example-configurations.md#multiple-cnames)
- [Using redirects](additional-docs/example-configurations.md#using-redirects)

See more [example-configurations](additional-docs/example-configurations.md)

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

## Alternatives

* <https://pages.github.com/>
* <https://pages.gitlab.io/>

## License

MIT. See the LICENSE file for more information.

## Contributors

This gem is created by Lauri Lehmijoki. Without the valuable work of [Philippe
Creux](https://github.com/pcreux) on
[jekyll-s3](https://github.com/laurilehmijoki/jekyll-s3), this project would not
exist.

See the [Contributors](https://github.com/laurilehmijoki/s3_website/graphs/contributors).

## Community articles

* [Deploying websites to FTP or Amazon S3 with BitBucket Pipelines](https://www.savjee.be/2016/06/Deploying-website-to-ftp-or-amazon-s3-with-BitBucket-Pipelines/)
* [How To: Hosting on Amazon S3 with CloudFront](https://paulstamatiou.com/hosting-on-amazon-s3-with-cloudfront/)
* [Zero to HTTP/2 with AWS and Hugo](https://habd.as/zero-to-http-2-aws-hugo/)

## Donations

[![Support via Gittip](https://rawgithub.com/twolfson/gittip-badge/0.2.0/dist/gittip.png)](https://www.gittip.com/laurilehmijoki/)
