package s3.website.model

import scala.util.{Failure, Try}
import scala.collection.JavaConversions._
import s3.website.Ruby.rubyRuntime
import s3.website.ErrorReport

case class Config(
  s3_id:                      String,
  s3_secret:                  String,
  s3_bucket:                  String,
  s3_endpoint:                S3Endpoint,
  max_age:                    Option[Either[Int, Map[String, Int]]],
  gzip:                       Option[Either[Boolean, Seq[String]]],
  gzip_zopfli:                Option[Boolean],
  ignore_on_server:           Option[Either[String, Seq[String]]],
  exclude_from_upload:        Option[Either[String, Seq[String]]],
  s3_reduced_redundancy:      Option[Boolean],
  cloudfront_distribution_id: Option[String],
  cloudfront_invalidate_root: Option[Boolean],
  redirects:                  Option[Map[String, String]],
  concurrency_level:          Int
)

object Config {
  def loadOptionalBooleanOrStringSeq(key: String)(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Either[Boolean, Seq[String]]]] = {
    val yamlValue = for {
      optionalValue <- loadOptionalValue(key)
    } yield {
      Right(optionalValue.map {
        case value if value.isInstanceOf[Boolean] => Left(value.asInstanceOf[Boolean])
        case value if value.isInstanceOf[java.util.List[_]] => Right(value.asInstanceOf[java.util.List[String]].toIndexedSeq)
      })
    }

    yamlValue getOrElse Left(ClientError(s"The key $key has to have a boolean or [string] value"))
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

    yamlValue getOrElse Left(ClientError(s"The key $key has to have a string or [string] value"))
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

    yamlValue getOrElse Left(ClientError(s"The key $key has to have an int or (string -> int) value"))
  }

  def loadEndpoint(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[S3Endpoint]] =
    loadOptionalString("s3_endpoint").right flatMap { endpointString =>
      endpointString.map(S3Endpoint.forString) match {
        case Some(Right(endpoint)) => Right(Some(endpoint))
        case Some(Left(endpointError)) => Left(endpointError)
        case None => Right(None)
      }
    }

  def loadRedirects(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, Option[Map[String, String]]] = {
    val key = "redirects"
    val yamlValue = for {
      redirectsOption <- loadOptionalValue(key)
      redirects <- Try(redirectsOption.map(_.asInstanceOf[java.util.Map[String,String]].toMap))
    } yield Right(redirects)

    yamlValue getOrElse Left(ClientError(s"The key $key has to have a (string -> string) value"))
  }

  def loadRequiredString(key: String)(implicit unsafeYaml: UnsafeYaml): Either[ErrorReport, String] = {
    val yamlValue = for {
      valueOption <- loadOptionalValue(key)
      stringValue <- Try(valueOption.asInstanceOf[Option[String]].get)
    } yield {
      Right(stringValue)
    }

    yamlValue getOrElse {
      Left(ClientError(s"The key $key has to have a string value"))
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
      Left(ClientError(s"The key $key has to have a string value"))
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
      Left(ClientError(s"The key $key has to have a boolean value"))
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
      Left(ClientError(s"The key $key has to have an integer value"))
    }
  }

  def loadOptionalValue(key: String)(implicit unsafeYaml: UnsafeYaml): Try[Option[_]] =
    Try {
      unsafeYaml.yamlObject.asInstanceOf[java.util.Map[String, _]].toMap get key
    }

  def erbEval(erbString: String, yamlConfigPath: String): Try[String] = Try {
    val erbStringWithoutComments = erbString.replaceAll("^\\s*#.*", "")
    rubyRuntime.evalScriptlet(
      s"""
        |require 'erb'
        |
        |str = <<-ERBSTR
        |$erbStringWithoutComments
        |ERBSTR
        |ERB.new(str).result
      """.stripMargin
    ).asJavaString()
  } match {
    case Failure(err) => Failure(new RuntimeException(s"Failed to parse ERB in $yamlConfigPath:\n${err.getMessage}"))
    case x => x
  }

  case class UnsafeYaml(yamlObject: AnyRef)
}