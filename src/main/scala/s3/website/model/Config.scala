package s3.website.model

import java.io.File
import java.util

import scala.util.matching.Regex
import scala.util.{Failure, Try}
import scala.collection.JavaConversions._
import s3.website.Ruby.rubyRuntime
import s3.website._
import com.amazonaws.auth.{AWSCredentialsProvider, BasicAWSCredentials, DefaultAWSCredentialsProviderChain}

case class Config(
  s3_id:                                  Option[String], // If undefined, use IAM Roles (http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-roles.html)
  s3_secret:                              Option[String], // If undefined, use IAM Roles (http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-roles.html)
  s3_bucket:                              String,
  s3_endpoint:                            S3Endpoint,
  site:                                   Option[String],
  max_age:                                Option[Either[Int, S3KeyGlob[Int]]],
  cache_control:                          Option[Either[String, S3KeyGlob[String]]],
  gzip:                                   Option[Either[Boolean, Seq[String]]],
  gzip_zopfli:                            Option[Boolean],
  s3_key_prefix:                          Option[String],
  ignore_on_server:                       Option[S3KeyRegexes],
  exclude_from_upload:                    Option[S3KeyRegexes],
  s3_reduced_redundancy:                  Option[Boolean],
  cloudfront_distribution_id:             Option[String],
  cloudfront_invalidate_root:             Option[Boolean],
  content_type:                           Option[S3KeyGlob[String]],
  redirects:                              Option[Map[S3Key, String]],
  concurrency_level:                      Int,
  cloudfront_wildcard_invalidation:       Option[Boolean],
  treat_zero_length_objects_as_redirects: Option[Boolean]
)

object Config {

  def awsCredentials(config: Config): AWSCredentialsProvider = {
    val credentialsFromConfigFile = for {
      s3_id <- config.s3_id
      s3_secret <- config.s3_secret
    } yield new BasicAWSCredentials(s3_id, s3_secret)
    credentialsFromConfigFile.fold(new DefaultAWSCredentialsProviderChain: AWSCredentialsProvider)(credentials =>
      new AWSCredentialsProvider {
        def getCredentials = credentials
        def refresh() = {}
      }
    )
  }

  def loadOptionalBooleanOrStringSeq(key: String)(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Either[Boolean, Seq[String]]]] = {
    val yamlValue = for {
      optionalValue <- loadOptionalValue(key)
    } yield {
      Right(optionalValue.map {
        case value if value.isInstanceOf[Boolean] => Left(value.asInstanceOf[Boolean])
        case value if value.isInstanceOf[java.util.List[_]] => Right(value.asInstanceOf[java.util.List[String]].toIndexedSeq)
      })
    }

    yamlValue getOrElse Left(ErrorReport(s"The key $key has to have a boolean or [string] value"))
  }

  def loadOptionalS3KeyRegexes(key: String)(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[S3KeyRegexes]] = {
    val yamlValue = for {
      valueOption <- loadOptionalValue(key)
    } yield {
      def toS3KeyRegexes(xs: Seq[String]) = S3KeyRegexes(xs map (str => str.r) map S3KeyRegex)
      Right(valueOption.map {
        case value if value.isInstanceOf[String] =>
          toS3KeyRegexes(value.asInstanceOf[String] :: Nil)
        case value if value.isInstanceOf[java.util.List[_]] =>
          toS3KeyRegexes(value.asInstanceOf[java.util.List[String]].toIndexedSeq)
      })
    }

    yamlValue getOrElse Left(ErrorReport(s"The key $key has to have a string or [string] value"))
  }

  def loadMaxAge(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Either[Int, S3KeyGlob[Int]]]] = {
    val key = "max_age"
    val yamlValue = for {
      maxAgeOption <- loadOptionalValue(key)
    } yield {
        // TODO below we are using an unsafe call to asInstance of – we should implement error handling
        Right(maxAgeOption.map {
          case maxAge if maxAge.isInstanceOf[Int] =>
            Left(maxAge.asInstanceOf[Int])
          case maxAge if maxAge.isInstanceOf[java.util.Map[_,_]] =>
            val globs: Map[String, Int] = maxAge.asInstanceOf[util.Map[String, Int]].toMap
            Right(S3KeyGlob(globs))
        })
      }

    yamlValue getOrElse Left(ErrorReport(s"The key $key has to have an int or (string -> int) value"))
  }

  def loadCacheControl(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Either[String, S3KeyGlob[String]]]] = {
    val key = "cache_control"
    val yamlValue = for {
      cacheControlOption <- loadOptionalValue(key)
    } yield {
        // TODO below we are using an unsafe call to asInstance of – we should implement error handling
        Right(cacheControlOption.map {
          case cacheControl if cacheControl.isInstanceOf[String] =>
            Left(cacheControl.asInstanceOf[String])
          case cacheControl if cacheControl.isInstanceOf[java.util.Map[_,_]] =>
            val globs: Map[String, String] = cacheControl.asInstanceOf[util.Map[String, String]].toMap
            Right(S3KeyGlob(globs))
        })
      }

    yamlValue getOrElse Left(ErrorReport(s"The key $key has to have a string or (string -> string) value"))
  }

  def loadContentType(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[S3KeyGlob[String]]] = {
    val key = "content_type"
    val yamlValue = for {
      contentTypeOption <- loadOptionalValue(key)
    } yield {
      // TODO below we are using an unsafe call to asInstance of – we should implement error handling
      Right(contentTypeOption.map { xs =>
          val globs: Map[String, String] = xs.asInstanceOf[util.Map[String, String]].toMap
          S3KeyGlob(globs)
        }
      )
    }

    yamlValue getOrElse Left(ErrorReport(s"The key $key has to have a string or (string -> string) value"))
  }


  def loadEndpoint(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[S3Endpoint]] =
    loadOptionalString("s3_endpoint").right map { endpointString =>
      endpointString.map(S3Endpoint.fromString)
    }

  def loadRedirects(s3_key_prefix: Option[String])(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Map[S3Key, String]]] = {
    val key = "redirects"
    val yamlValue = for {
      redirectsOption <- loadOptionalValue(key)
      redirectsOption <- Try(redirectsOption.map(_.asInstanceOf[java.util.Map[String,String]].toMap))
    } yield Right(redirectsOption.map(
        redirects => redirects.map(
          ((key: String, value: String) => (S3Key.build(key, s3_key_prefix), value)).tupled
        )
      ))

    yamlValue getOrElse Left(ErrorReport(s"The key $key has to have a (string -> string) value"))
  }

  def loadRequiredString(key: String)(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, String] = {
    val yamlValue = for {
      valueOption <- loadOptionalValue(key)
      stringValue <- Try(valueOption.asInstanceOf[Option[String]].get)
    } yield {
      Right(stringValue)
    }

    yamlValue getOrElse {
      Left(ErrorReport(s"The key $key has to have a string value"))
    }
  }

  def loadOptionalString(key: String)(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[String]] = {
    val yamlValueOption = for {
      valueOption <- loadOptionalValue(key)
      optionalString <- Try(valueOption.asInstanceOf[Option[String]])
    } yield {
      Right(optionalString)
    }

    yamlValueOption getOrElse {
      Left(ErrorReport(s"The key $key has to have a string value"))
    }
  }

  def loadOptionalBoolean(key: String)(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Boolean]] = {
    val yamlValueOption = for {
      valueOption <- loadOptionalValue(key)
      optionalBoolean <- Try(valueOption.asInstanceOf[Option[Boolean]])
    } yield {
      Right(optionalBoolean)
    }

    yamlValueOption getOrElse {
      Left(ErrorReport(s"The key $key has to have a boolean value"))
    }
  }

  def loadOptionalInt(key: String)(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Int]] = {
    val yamlValueOption = for {
      valueOption <- loadOptionalValue(key)
      optionalInt <- Try(valueOption.asInstanceOf[Option[Int]])
    } yield {
      Right(optionalInt)
    }

    yamlValueOption getOrElse {
      Left(ErrorReport(s"The key $key has to have an integer value"))
    }
  }

  def loadOptionalValue(key: String)(implicit unsafeYaml: UnsafeYaml): Try[Option[_]] =
    Try {
      unsafeYaml.yamlObject.asInstanceOf[java.util.Map[String, _]].toMap get key
    }

  def erbEval(erbString: String, yamlConfig: S3_website_yml): Try[String] = Try {
    val erbStringWithoutComments = erbString.replaceAll("^\\s*#.*", "")
    rubyRuntime.evalScriptlet(
      s"""|# encoding: utf-8
        |require 'erb'
        |
        |str = <<-ERBSTR
        |$erbStringWithoutComments
        |ERBSTR
        |ERB.new(str).result
      """.stripMargin
    ).asJavaString()
  } match {
    case Failure(err) => Failure(new RuntimeException(s"Failed to parse ERB in $yamlConfig:\n${err.getMessage}"))
    case x => x
  }

  case class UnsafeYaml(yamlObject: AnyRef)

  case class S3_website_yml(file: File) {
    override def toString = file.getPath
  }
}