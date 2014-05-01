package com.github.laurilehmijoki.model

import java.io.File
import scala.util.{Failure, Success, Try}
import org.yaml.snakeyaml.Yaml
import com.github.laurilehmijoki.model.Config._
import scala.io.Source.fromFile
import scala.language.postfixOps

case class Site(rootDirectory: String, config: Config) {
  def localFilePath(file: File) = file.getAbsolutePath.replace(rootDirectory, "").replaceFirst("^/", "")
}

object Site {
  def loadSite(yamlConfigPath: String, siteRootDirectory: String): Either[Error, Site] = {
    val yamlObjectTry = for {
      yamlString <- Try(fromFile(new File(yamlConfigPath)).mkString)
      yamlWithErbEvaluated <- erbEval(yamlString)
      yamlObject <- Try(new Yaml() load yamlWithErbEvaluated)
    } yield yamlObject
    yamlObjectTry match {
      case Success(yamlObject) =>
        implicit val unsafeYaml = UnsafeYaml(yamlObject)
        val config: Either[Error, Config] = for {
          s3_id <- loadRequiredString("s3_id").right
          s3_secret <- loadRequiredString("s3_secret").right
          s3_bucket <- loadRequiredString("s3_bucket").right
          s3_endpoint <- loadEndpoint.right
          max_age <- loadMaxAge.right
          gzip <- loadOptionalBooleanOrStringSeq("gzip").right
          gzip_zopfli <- loadOptionalBoolean("gzip_zopfli").right
          extensionless_mime_type <- loadOptionalString("extensionless_mime_type").right
          ignore_on_server <- loadOptionalStringOrStringSeq("ignore_on_server").right
          exclude_from_upload <- loadOptionalStringOrStringSeq("exclude_from_upload").right
          s3_reduced_redundancy <- loadOptionalBoolean("s3_reduced_redundancy").right
          cloudfront_distribution_id <- loadOptionalString("cloudfront_distribution_id").right
          cloudfront_invalidate_root <- loadOptionalBoolean("cloudfront_invalidate_root").right
          concurrency_level <- loadOptionalInt("concurrency_level").right
          redirects <- loadRedirects.right
        } yield {
          Config(
            s3_id,
            s3_secret,
            s3_bucket,
            s3_endpoint getOrElse S3Endpoint.defaultEndpoint,
            max_age,
            gzip,
            gzip_zopfli,
            extensionless_mime_type,
            ignore_on_server = ignore_on_server,
            exclude_from_upload = exclude_from_upload,
            s3_reduced_redundancy,
            cloudfront_distribution_id,
            cloudfront_invalidate_root,
            redirects,
            concurrency_level.fold(20)(_ max 20) // At minimum, run 20 concurrent operations
          )
        }

        config.right.map {
          config =>
            Site(siteRootDirectory, config)
        }
      case Failure(error) =>
        Left(IOError(error))
    }
  }
}