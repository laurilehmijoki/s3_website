# Example `s3_website` configurations

This document shows examples of complete `s3_website.yml` configurations.

## Minimal

````yaml
s3_id: abcd
s3_secret: 2s+x92
s3_bucket: your.domain.net
````

## Minimal with EC2 IAM roles

````yaml
s3_bucket: your.domain.net
````

If you run `s3_website` on an EC2 instance with IAM roles, it is possible to omit
the `s3_id` and `s3_secret`.

## Optimised for speed

Use CloudFront, gzip, cache headers and greater concurrency:

````yaml
s3_id: <%= ENV['your_domain_net_aws_key'] %>
s3_secret: <%= ENV['your_domain_net_aws_secret'] %>
s3_bucket: your.domain.net
cloudfront_distribution_id: <%= ENV['your_domain_net_cloudfront_distribution_id'] %>
cloudfront_distribution_config:
  default_cache_behavior:
    min_TTL: <%= 60 * 60 * 24 %>
  aliases:
    quantity: 1
    items:
      CNAME: your.domain.net
max_age: 120
gzip: true
````

Above, we store the AWS credentials and the id of the CloudFront distribution as
environment variables. It's convenient, since you can keep the `s3_website.yml`
in a public Git repo, and thus have your deployment configurations
version-controlled.

## Multiple CNAMEs

Sometimes you want to use multiple CNAMEs aliases in your CloudFront distribution:

````yaml
s3_id: <%= ENV['your_domain_net_aws_key'] %>
s3_secret: <%= ENV['your_domain_net_aws_secret'] %>
s3_bucket: your.domain.net
cloudfront_distribution_id: <%= ENV['your_domain_net_cloudfront_distribution_id'] %>
cloudfront_distribution_config:
  default_cache_behavior:
    min_TTL: <%= 60 * 60 * 24 %>
  aliases:
    quantity: 3
    items:
      CNAME0: your1.domain.net
      CNAME1: your2.domain.net
      CNAME2: your3.domain.net
max_age: 120
gzip: true
````

Always remember to set the 'quantity' property to match the number of items you have.

## Using redirects

````yaml
s3_id: hello
s3_secret: galaxy
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
