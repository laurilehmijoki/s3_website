package s3.website.model

import java.io.File
import s3.website.Push.CliArgs
import s3.website.model.Ssg.autodetectSiteDir

import scala.util.Try
import org.yaml.snakeyaml.Yaml
import s3.website.model.Config._
import scala.io.Source.fromFile
import scala.language.postfixOps
import s3.website.{S3Key, Logger, ErrorReport}
import scala.util.Failure
import s3.website.model.Config.UnsafeYaml
import scala.util.Success

case class Site(rootDirectory: File, config: Config) {
  def resolveS3Key(file: File) = file.getAbsolutePath.replace(rootDirectory.getAbsolutePath, "").replace(File.separator,"/").replaceFirst("^/", "")

  def resolveFile(s3File: S3File): File = resolveFile(s3File.s3Key)

  def resolveFile(s3Key: S3Key): File = new File(s"$rootDirectory/$s3Key")
}

object Site {
  def parseConfig(implicit logger: Logger, yamlConfig: S3_website_yml): Either[ErrorReport, Config] = {
    val yamlObjectTry = for {
      yamlString <- Try(fromFile(yamlConfig.file).mkString)
      yamlWithErbEvaluated <- erbEval(yamlString, yamlConfig)
      yamlObject <- Try(new Yaml() load yamlWithErbEvaluated)
    } yield yamlObject

    yamlObjectTry match {
      case Success(yamlObject) =>
        implicit val unsafeYaml = UnsafeYaml(yamlObject)
        for {
          s3_id <- loadOptionalString("s3_id").right
          s3_secret <- loadOptionalString("s3_secret").right
          s3_bucket <- loadRequiredString("s3_bucket").right
          s3_endpoint <- loadEndpoint.right
          site <- loadOptionalString("site").right
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
          treat_zero_length_objects_as_redirects <- loadOptionalBoolean("treat_zero_length_objects_as_redirects").right
        } yield {
          gzip_zopfli.foreach(_ => logger.info(
            """|Zopfli is not currently supported. Falling back to regular gzip.
              |If you find a stable Java implementation for zopfli, please send an email to lauri.lehmijoki@iki.fi about it."""
              .stripMargin))
          extensionless_mime_type.foreach(_ => logger.info(
            s"Ignoring the extensionless_mime_type setting in $yamlConfig. Counting on Apache Tika to infer correct mime types.")
          )
          Config(
            s3_id,
            s3_secret,
            s3_bucket,
            s3_endpoint getOrElse S3Endpoint.defaultEndpoint,
            site,
            max_age,
            gzip,
            gzip_zopfli,
            ignore_on_server = ignore_on_server,
            exclude_from_upload = exclude_from_upload,
            s3_reduced_redundancy,
            cloudfront_distribution_id,
            cloudfront_invalidate_root,
            redirects,
            concurrency_level.fold(20)(_ max 20),
            treat_zero_length_objects_as_redirects
          )
        }
      case Failure(error) =>
        Left(ErrorReport(error))
    }
  }

  def loadSite(implicit yamlConfig: S3_website_yml, cliArgs: CliArgs, workingDirectory: File, logger: Logger): Either[ErrorReport, Site] =
    parseConfig.right.flatMap { cfg =>
      implicit val config: Config = cfg
      val errorOrSiteDir = resolveSiteDir.fold(Left(ErrorReport(noSiteFound)): Either[ErrorReport, File])(Right(_))
      errorOrSiteDir.right.map(Site(_, config))
    }

  val noSiteFound =
    """|Could not find a website.
       |Either use the --site=DIR command-line argument or define the location of the site in s3_website.yml.
       |
       |Here's an example of how you can define the site directory in s3_website.yml:
       |   site: dist/website""".stripMargin

  def resolveSiteDir(implicit yamlConfig: S3_website_yml, config: Config, cliArgs: CliArgs, workingDirectory: File): Option[File] = {
    val siteFromAutoDetect = autodetectSiteDir(workingDirectory)
    val siteFromCliArgs = Option(cliArgs.site).map(new File(_))

    siteFromCliArgs orElse siteFromConfig orElse siteFromAutoDetect
  }

  def siteFromConfig(implicit yamlConfig: S3_website_yml, config: Config, workingDirectory: File): Option[File] =
    config
      .site
      .map(new File(_))
      .map { siteDir =>
        if (siteDir.isAbsolute) siteDir
        else new File(yamlConfig.file.getParentFile, siteDir.getPath)
      }
}