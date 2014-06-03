package s3.website.model

import java.io.File
import scala.util.Try
import org.yaml.snakeyaml.Yaml
import s3.website.model.Config._
import scala.io.Source.fromFile
import scala.language.postfixOps
import s3.website.{S3Key, Logger, ErrorReport}
import scala.util.Failure
import s3.website.model.Config.UnsafeYaml
import scala.util.Success

case class Site(rootDirectory: String, config: Config) {
  def resolveS3Key(file: File) = file.getAbsolutePath.replace(rootDirectory, "").replaceFirst("^/", "")

  def resolveFile(s3File: S3File): File = resolveFile(s3File.s3Key)

  def resolveFile(s3Key: S3Key): File = new File(s"$rootDirectory/$s3Key")
}

object Site {
  def loadSite(yamlConfigPath: String, siteRootDirectory: String)
              (implicit logger: Logger): Either[ErrorReport, Site] = {
    val yamlObjectTry = for {
      yamlString <- Try(fromFile(new File(yamlConfigPath)).mkString)
      yamlWithErbEvaluated <- erbEval(yamlString, yamlConfigPath)
      yamlObject <- Try(new Yaml() load yamlWithErbEvaluated)
    } yield yamlObject
    yamlObjectTry match {
      case Success(yamlObject) =>
        implicit val unsafeYaml = UnsafeYaml(yamlObject)
        val config: Either[ErrorReport, Config] = for {
          s3_id <- loadOptionalString("s3_id").right
          s3_secret <- loadOptionalString("s3_secret").right
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
          gzip_zopfli.foreach(_ => logger.info(
            """|Zopfli is not currently supported. Falling back to regular gzip.
               |If you find a stable Java implementation for zopfli, please send an email to lauri.lehmijoki@iki.fi about it."""
            .stripMargin))
          extensionless_mime_type.foreach(_ => logger.info(
            s"Ignoring the extensionless_mime_type setting in $yamlConfigPath. Counting on Apache Tika to infer correct mime types.")
          )
          Config(
            s3_id,
            s3_secret,
            s3_bucket,
            s3_endpoint getOrElse S3Endpoint.defaultEndpoint,
            max_age,
            gzip,
            gzip_zopfli,
            ignore_on_server = ignore_on_server,
            exclude_from_upload = exclude_from_upload,
            s3_reduced_redundancy,
            cloudfront_distribution_id,
            cloudfront_invalidate_root,
            redirects,
            concurrency_level.fold(20)(_ max 20) // At minimum, run 20 concurrent operations
          )
        }

        config.right.map(Site(siteRootDirectory, _))
      case Failure(error) =>
        Left(ErrorReport(error))
    }
  }
}