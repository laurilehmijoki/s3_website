package s3.website.model

import java.io.File

import scala.util.{Failure, Try}
import scala.collection.JavaConversions._
import s3.website.Ruby.rubyRuntime
import s3.website.{S3Key, ErrorReport}
import com.amazonaws.auth.{AWSCredentialsProvider, BasicAWSCredentials, DefaultAWSCredentialsProviderChain}

case class Config(
  s3_id:                      Option[String], // If undefined, use IAM Roles (http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-roles.html)
  s3_secret:                  Option[String], // If undefined, use IAM Roles (http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-roles.html)
  s3_bucket:                  String,
  s3_endpoint:                S3Endpoint,
  site:                       Option[String],
  max_age:                    Option[Either[Int, Map[String, Int]]],
  cache_control:              Option[Either[String, Map[String, String]]],
  gzip:                       Option[Either[Boolean, Seq[String]]],
  gzip_zopfli:                Option[Boolean],
  ignore_on_server:           Option[Either[String, Seq[String]]],
  exclude_from_upload:        Option[Either[String, Seq[String]]],
  s3_reduced_redundancy:      Option[Boolean],
  cloudfront_distribution_id: Option[String],
  cloudfront_invalidate_root: Option[Boolean],
  redirects:                  Option[Map[S3Key, String]],
  concurrency_level:          Int,
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

  def loadOptionalStringOrStringSeq(key: String)(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Either[String, Seq[String]]]] = {
    val yamlValue = for {
      valueOption <- loadOptionalValue(key)
    } yield {
      Right(valueOption.map {
        case value if value.isInstanceOf[String] => Left(value.asInstanceOf[String])
        case value if value.isInstanceOf[java.util.List[_]] => Right(value.asInstanceOf[java.util.List[String]].toIndexedSeq)
      })
    }

    yamlValue getOrElse Left(ErrorReport(s"The key $key has to have a string or [string] value"))
  }

  def loadMaxAge(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Either[Int, Map[String, Int]]]] = {
    val key = "max_age"
    val yamlValue = for {
      maxAgeOption <- loadOptionalValue(key)
    } yield {
      Right(maxAgeOption.map {
        case maxAge if maxAge.isInstanceOf[Int] => Left(maxAge.asInstanceOf[Int])
        case maxAge if maxAge.isInstanceOf[java.util.Map[_,_]] => Right(maxAge.asInstanceOf[java.util.Map[String,Int]].toMap)
      })
    }

    yamlValue getOrElse Left(ErrorReport(s"The key $key has to have an int or (string -> int) value"))
  }

  def loadCacheControl(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Either[String, Map[String, String]]]] = {
    val key = "cache_control"
    val yamlValue = for {
      cacheControlOption <- loadOptionalValue(key)
    } yield {
        Right(cacheControlOption.map {
          case cacheControl if cacheControl.isInstanceOf[String] => Left(cacheControl.asInstanceOf[String])
          case cacheControl if cacheControl.isInstanceOf[java.util.Map[_,_]] => Right(cacheControl.asInstanceOf[java.util.Map[String,String]].toMap) // TODO an unsafe call to asInstanceOf
        })
      }

    yamlValue getOrElse Left(ErrorReport(s"The key $key has to have a string or (string -> string) value"))
  }

  def loadEndpoint(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[S3Endpoint]] =
    loadOptionalString("s3_endpoint").right flatMap { endpointString =>
      endpointString.map(S3Endpoint.forString) match {
        case Some(Right(endpoint)) => Right(Some(endpoint))
        case Some(Left(endpointError)) => Left(endpointError)
        case None => Right(None)
      }
    }

  def loadRedirects(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Map[S3Key, String]]] = {
    val key = "redirects"
    val yamlValue = for {
      redirectsOption <- loadOptionalValue(key)
      redirectsOption <- Try(redirectsOption.map(_.asInstanceOf[java.util.Map[String,String]].toMap))
    } yield Right(redirectsOption.map(
        redirects => redirects.map(
          ((key: String, value: String) => (S3Key(key), value)).tupled
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