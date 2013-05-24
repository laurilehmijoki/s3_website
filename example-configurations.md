# Example `jekyll-s3` configurations

This document shows examples of complete `_jekyll_s3.yml` configurations.

## Minimal

````yaml
s3_id: abcd
s3_secret: 2s+x92
s3_bucket: your.domain.net
````

## Minimal with EC2 AIM roles

````yaml
s3_bucket: your.domain.net
````

If you run `jekyll-s3` on an EC2 instance with IAM roles, it is possible to omit
the `s3_id` and `s3_secret`.

## Optimised for speed: using CloudFront, gzip and cache headers

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
environment variables. It's convenient, since you can keep the `_jekyll_s3.yml`
in a public Git repo, and thus have your deployment configurations
version-controlled.
