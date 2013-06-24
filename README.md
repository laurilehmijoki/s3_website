# s3_website – Deploy your website to S3.

[![Build Status](https://secure.travis-ci.org/laurilehmijoki/s3_website.png)] (http://travis-ci.org/laurilehmijoki/s3_website)
[![Gem Version](https://fury-badge.herokuapp.com/rb/s3_website.png)](http://badge.fury.io/rb/s3_website)

## What s3_website can do for you

* Upload your site to AWS S3
* Help you use AWS Cloudfront to distribute your website
* Create an S3 website for you
* Improve page speed with HTTP cache control and gzipping
* Set HTTP redirects for your website
* (for other features, see the documentation below)

## Install

    gem install s3_website

## Usage

* Go to your website directory
* Run `s3_website`. It generates a configuration file called `s3_website.yml` that looks like this:
<pre>
s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
s3_bucket: your.blog.bucket.com
</pre>
* Edit it with your details (you can use [ERB](http://ruby-doc.org/stdlib-1.9.3/libdoc/erb/rdoc/ERB.html) in the file)
* Run `configure-s3-website --config-file s3_website.yml` This will configure
  your bucket to function as an S3 website. If the bucket does not exist,
  `configure-s3-website` will create it for you.

* Run `s3_website` to push your website to S3. Congratulations! You are live.

(If you are using `s3_website` on an [EC2 instance with IAM
roles](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/UsingIAM.html#UsingIAMrolesWithAmazonEC2Instances),
you can omit the `s3_id` and `s3_secret` keys in the config file.)

### Using environment variables

You can use ERB in your `s3_website.yml` file which incorporates environment variables:

```yaml
s3_id: <%= ENV['S3_ID'] %>
s3_secret: <%= ENV['S3_SECRET'] %>
s3_bucket: blog.example.com
```

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
* Maintain 90% backward compatibility with the jekyll-s3 gem

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

### Reduced Redundancy

You can reduce the cost of hosting your blog on S3 by using Reduced Redundancy Storage:

  * In `s3_website.yml`, set `s3_reduced_redundancy: true`
  * All objects uploaded after this change will use the Reduced Redundancy Storage.
  * If you want to change all of the files in the bucket, you can change them through the AWS console, or update the timestamp on the files before running `s3_website` again

### How to use Cloudfront to deliver your blog

It is easy to deliver your S3-based web site via Cloudfront, the CDN of Amazon.

#### Creating a new CloudFront distribution

When you run the command `configure-s3-website`, it will ask you whether you
want to deliver your website via CloudFront. If you answer yes,
`configure-s3-website` will create a CloudFront distribution for you.

This feature was added into the version 1.3.0 of the `configure-s3-website` gem.
For more information, see the [gem's
documentation](https://github.com/laurilehmijoki/configure-s3-website).

#### Using your existing CloudFront distribution

If you already have a CloudFront distribution that serves data from your website
S3 bucket, just add the following line into the file `s3_website.yml`:

    cloudfront_distribution_id: your-dist-id

Next time you run `s3_website`, it will invalidate the items on CloudFront and
thus force the CDN system to reload the changes from your website S3 bucket.

#### Specifying custom settings for your CloudFront distribution

The gem `configure-s3-website`, which is a dependency of `s3_website`, lets you
define custom settings for your CloudFront distribution.

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

See the [gem's
documentation](https://github.com/laurilehmijoki/configure-s3-website) for more
info.

### The headless mode

s3_website has a headless mode, where human interactions are disabled.

In the headless mode, `s3_website` will automatically delete the files on the S3
bucket that are not on your local computer.

Enable the headless mode by adding the `--headless` or `-h` argument after
`s3_website`.

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
  about.php: about.html
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

After adding the configuration, run the command `configure-s3-website --config
s3_website.yml` on your command-line interface. This will apply the routing
rules on your S3 bucket.

For more information on configuring redirects, see the documentation of the
[configure-s3-website](https://github.com/laurilehmijoki/configure-s3-website#configuring-redirects)
gem, which comes as a transitive dependency of the `s3_website` gem.

### Using `s3_website` as a library

By nature, `s3_website` is a command-line interface tool. You can, however, use
it programmatically by calling the same API as the executable `s3_website` does:

````ruby
require 's3_website'
is_headless = true
S3Website::Tasks.push('/path/to/your/website/_site/', is_headless)
````

You can also use a basic `Hash` instead of a `s3_website.yml` file:

```ruby
config = {
  "s3_id"     => YOUR_AWS_S3_ACCESS_KEY_ID,
  "s3_secret" => YOUR_AWS_S3_SECRET_ACCESS_KEY,
  "s3_bucket" => "your.blog.bucket.com"
}
in_headless = true
S3Website::Uploader.run('/path/to/your/website/_site/', config, in_headless)
```

The code above will assume that you have the `s3_website.yml` in the directory
`/path/to/your/website`.

## Example configurations

See
<https://github.com/laurilehmijoki/s3_website/blob/master/example-configurations.md>.

## Known issues

None. Please send a pull request if you spot any.

## Development

### Versioning

s3_website uses [Semantic Versioning](http://semver.org).

### Tests

  * Install bundler and run `bundle install`
  * Run all tests by invoking `rake test`
  * Run the integration tests by running `bundle exec cucumber`
  * Run the unit tests by running `bundle exec rspec spec/lib/*.rb`

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

This gem is created by [Lauri Lehmijoki](https://github.com/laurilehmijoki).
Without the valuable work of [Philippe Creux](https://github.com/pcreux) on
[jekyll-s3](https://github.com/laurilehmijoki/jekyll-s3), this project would not
exist.

Contributors (in alphabetical order)
* Alan deLevie
* Cory Kaufman-Schofield
* Chris Kelly
* Chris Moos
* David Michael Barr
* László Bácsi
* Mason Turner
* Michael Bleigh
* Philippe Creux
* Shigeaki Matsumura
* stanislas
* Trevor Fitzgerald
* Zee Spencer
