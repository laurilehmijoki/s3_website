# Setting up AWS credentials

Before starting to use s3\_website, you need to create AWS credentials.

## Easy setup

* Go to [AWS IAM console](https://console.aws.amazon.com/iam)
* Create a new user that has full permissions to the S3 and CloudFront services
* Call `s3_website cfg create` and place the credentials of your new AWS user
  into the *s3_website.yml* file
* Read the main documentation for further info

## Limiting the permissions of the credentials

AWS IAM offers multiple ways of limiting the permissions of a user. Below is one
way of configuring the limitations and yet retaining the capability to use all
s3\_website features.

If you know the hostname of your public website (say `my.website.com`), perform the
following steps:

* Create a user that has full permissions to the S3 bucket
* In addition, let the user have full permissions to CloudFront

Here is the IAM Policy Document of the above setup:

```json
{
  "Version":"2012-10-17",
  "Statement": [
    {
      "Action": [
        "cloudfront:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "*"
      ]
    },
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "arn:aws:s3:::my.website.com",
        "arn:aws:s3:::my.website.com/*"
      ]
    }
  ]
}
```
