package s3.website
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{BasicAWSCredentials, BasicSessionCredentials, DefaultAWSCredentialsProviderChain, STSAssumeRoleSessionCredentialsProvider}
import org.specs2.mutable.Specification
import s3.website.model.{Config, S3Endpoint}

class ConfigSpec extends Specification {

  "Config#awsCredentials" should {
    s"return ${classOf[BasicSessionCredentials]} when s3_id, s3_secret and session_token are defined in the config" in {
      Config.awsCredentials(Config(
        s3_id = Some("test"),
        s3_secret = Some("secret"),
        session_token = Some("Token"),
        profile = None,
        profile_assume_role_arn = None,
        s3_bucket = "foo",
        s3_endpoint = S3Endpoint.defaultEndpoint,
        site = None,
        max_age = None,
        cache_control = None,
        gzip = None,
        gzip_zopfli = None,
        s3_key_prefix = None,
        ignore_on_server = None,
        exclude_from_upload = None,
        s3_reduced_redundancy = None,
        cloudfront_distribution_id = None,
        cloudfront_invalidate_root = None,
        content_type = None,
        redirects = None,
        concurrency_level = 1,
        cloudfront_wildcard_invalidation = None,
        treat_zero_length_objects_as_redirects = None
      )).getCredentials must beAnInstanceOf[BasicSessionCredentials]
    }

    s"return ${classOf[BasicAWSCredentials]} when s3_id and s3_secret are defined in the config" in {
      Config.awsCredentials(Config(
        s3_id = Some("test"),
        s3_secret = Some("secret"),
        session_token = None,
        profile = None,
        profile_assume_role_arn = None,
        s3_bucket = "foo",
        s3_endpoint = S3Endpoint.defaultEndpoint,
        site = None,
        max_age = None,
        cache_control = None,
        gzip = None,
        gzip_zopfli = None,
        s3_key_prefix = None,
        ignore_on_server = None,
        exclude_from_upload = None,
        s3_reduced_redundancy = None,
        cloudfront_distribution_id = None,
        cloudfront_invalidate_root = None,
        content_type = None,
        redirects = None,
        concurrency_level = 1,
        cloudfront_wildcard_invalidation = None,
        treat_zero_length_objects_as_redirects = None
      )).getCredentials must beAnInstanceOf[BasicAWSCredentials]
    }

    s"return ${classOf[STSAssumeRoleSessionCredentialsProvider]} when profile and profile_assume_role_arn are defined in the config" in {
      Config.awsCredentials(Config(
        s3_id = None,
        s3_secret = None,
        session_token = None,
        profile = Some("profile_name"),
        profile_assume_role_arn = Some("arn:aws:iam::account-id:role/role-name"),
        s3_bucket = "foo",
        s3_endpoint = S3Endpoint.defaultEndpoint,
        site = None,
        max_age = None,
        cache_control = None,
        gzip = None,
        gzip_zopfli = None,
        s3_key_prefix = None,
        ignore_on_server = None,
        exclude_from_upload = None,
        s3_reduced_redundancy = None,
        cloudfront_distribution_id = None,
        cloudfront_invalidate_root = None,
        content_type = None,
        redirects = None,
        concurrency_level = 1,
        cloudfront_wildcard_invalidation = None,
        treat_zero_length_objects_as_redirects = None
      )) must beAnInstanceOf[STSAssumeRoleSessionCredentialsProvider]
    }

    s"return ${classOf[ProfileCredentialsProvider]} when profile is defined in the config" in {
      Config.awsCredentials(Config(
        s3_id = None,
        s3_secret = None,
        session_token = None,
        profile = Some("profile_name"),
        profile_assume_role_arn = None,
        s3_bucket = "foo",
        s3_endpoint = S3Endpoint.defaultEndpoint,
        site = None,
        max_age = None,
        cache_control = None,
        gzip = None,
        gzip_zopfli = None,
        s3_key_prefix = None,
        ignore_on_server = None,
        exclude_from_upload = None,
        s3_reduced_redundancy = None,
        cloudfront_distribution_id = None,
        cloudfront_invalidate_root = None,
        content_type = None,
        redirects = None,
        concurrency_level = 1,
        cloudfront_wildcard_invalidation = None,
        treat_zero_length_objects_as_redirects = None
      )) must beAnInstanceOf[ProfileCredentialsProvider]
    }

    s"return ${classOf[DefaultAWSCredentialsProviderChain]} when s3_id, s3_secret, profile and profile_assume_role_arn are not defined in the config" in {
      Config.awsCredentials(Config(
        s3_id = None,
        s3_secret = None,
        session_token = None,
        profile = None,
        profile_assume_role_arn = None,
        s3_bucket = "foo",
        s3_endpoint = S3Endpoint.defaultEndpoint,
        site = None,
        max_age = None,
        cache_control = None,
        gzip = None,
        gzip_zopfli = None,
        s3_key_prefix = None,
        ignore_on_server = None,
        exclude_from_upload = None,
        s3_reduced_redundancy = None,
        cloudfront_distribution_id = None,
        cloudfront_invalidate_root = None,
        content_type = None,
        redirects = None,
        concurrency_level = 1,
        cloudfront_wildcard_invalidation = None,
        treat_zero_length_objects_as_redirects = None
      )) must beAnInstanceOf[DefaultAWSCredentialsProviderChain]
    }
  }
}
