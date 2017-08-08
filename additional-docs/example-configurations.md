# Example `s3_website` configurations

This document shows examples of complete `s3_website.yml` configurations.

## Minimal

````yaml
s3_bucket: your.domain.net
````

This configuration will use AWS access credentials from the environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`. If those are not set, it will fall back to the credentials saved by `aws configure`.

If you run `s3_website` on an EC2 instance with IAM roles, this configuration will use the instance's role instead.

## Minimal with explicit credentials

````yaml
s3_id: abcd
s3_secret: 2s+x92
s3_bucket: your.domain.net
````

Use caution when embedding AWS credentials directly in `s3_website.yml`. Do not commit the file to a public Git repository or share it publicly.

## Minimal for temporary security credentials

````yaml
s3_id: abcd
s3_secret: 2s+x92
session_token: hex!xeh
s3_bucket: your.domain.net
````

## Optimised for speed

Use CloudFront, gzip, cache headers and greater concurrency:

````yaml
s3_bucket: your.domain.net
cloudfront_distribution_id: <%= ENV['your_domain_net_cloudfront_distribution_id'] %>
cloudfront_distribution_config:
  default_cache_behavior:
    min_ttl: <%= 60 * 60 * 24 %>
  aliases:
    quantity: 1
    items:
      - your.domain.net
max_age: 120
gzip: true
````

In this example, we keep the CloudFront distribution ID in an environment variable.
This is convenient, since you can keep the `s3_website.yml` in a public Git repo, and
thus have your deployment configurations version-controlled.

## Setup for HTTP2 and Custom SNI SSL Certificate

To fully utilize HTTP2 you'll need to setup SSL for your distribution. While HTTP/2 does
not mandate the use of encryption, it turns out that [all of the common web browsers 
require the use of HTTPS connections in conjunction with HTTP/2](http://caniuse.com/#feat=http2).
Therefore, you may need to make some changes to your site or application in order 
to take full advantage of HTTP/2. While you can test the site by using the Default
CloudFront Certificate you will likely want to use a custom SSL Certificate. 
This isn't yet automated by s3_website, [but is a few manual steps](https://medium.com/@richardkall/setup-lets-encrypt-ssl-certificate-on-amazon-cloudfront-b217669987b2#.7jyust8os), 
which is now free thanks to Let's Encrypt. 

````yaml
s3_bucket: your.domain.net
cloudfront_distribution_id: <%= ENV['your_domain_net_cloudfront_distribution_id'] %>
cloudfront_distribution_config:
  default_cache_behavior:
    min_ttl: <%= 60 * 60 * 24 %>
  http_version: http2
max_age: 120
gzip: true
````

## Multiple CNAMEs

Sometimes you want to use multiple CNAMEs aliases in your CloudFront distribution:

````yaml
s3_bucket: your.domain.net
cloudfront_distribution_id: <%= ENV['your_domain_net_cloudfront_distribution_id'] %>
cloudfront_distribution_config:
  default_cache_behavior:
    min_ttl: <%= 60 * 60 * 24 %>
  aliases:
    quantity: 3
    items:
      - your1.domain.net
      - your2.domain.net
      - your3.domain.net
max_age: 120
gzip: true
````

Always remember to set the 'quantity' property to match the number of items you have.

## Using redirects

````yaml
redirects:
  index.php: /
  about.php: about.html
routing_rules:
  - condition:
      key_prefix_equals: code/repositories/git/
    redirect:
      host_name: git.johnny.com
      replace_key_prefix_with: ""
      http_redirect_code: 301
````
