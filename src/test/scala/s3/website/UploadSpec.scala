package s3.website

import org.specs2.mutable.{After, Specification}
import s3.website.model._
import org.specs2.specification.Scope
import java.io.File.createTempFile
import org.apache.commons.io.FileUtils.write
import org.apache.commons.io.{FileUtils, IOUtils}
import java.util.zip.GZIPInputStream
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import scala.util.Random
import org.mockito.{Matchers, ArgumentCaptor}
import org.mockito.Mockito._
import com.amazonaws.services.s3.AmazonS3
import s3.website.model.Encoding.Gzip
import scala.Some
import com.amazonaws.services.s3.model._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._
import s3.website.S3.S3ClientProvider
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import s3.website.model.Encoding.Gzip
import s3.website.model.NewFile
import scala.Some

class UploadSpec extends Specification {

  "gzip" should {
    "gzip the file" in new TempFile {
      val upload = LocalFile.toUpload(LocalFile("index.html", tempFile, Some(Left(Gzip())))).right.get
      IOUtils.toString(new GZIPInputStream(upload.essence.right.get.openInputStream())) must equalTo(fileContents)
    }

    "calculate the md5 for the gzipped data" in new TempFile {
      val upload = LocalFile.toUpload(LocalFile("index.html", tempFile, Some(Left(Gzip())))).right.get
      upload.essence.right.get.md5 must equalTo("9b0474867213eeedf33dfe055680e7fa")
    }

    "calculate the md5 for the non-gzipped data" in new TempFile {
      val upload = LocalFile.toUpload(LocalFile("index.html", tempFile, None)).right.get
      upload.essence.right.get.md5 must equalTo(DigestUtils.md5Hex(fileContents))
    }
  }

  "max-age in config" can {
    "be applied to all files" in new SiteDirectory with MockS3 {
      implicit val site = buildSite(defaultConfig.copy(max_age = Some(Left(60))), files = "index.html" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=60")
    }

    "be applied to files that match the glob" in new SiteDirectory with MockS3 {
      implicit val site = buildSite(defaultConfig.copy(max_age = Some(Right(Map("*.html" -> 90)))), files = "index.html" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=90")
    }

    "be applied to directories that match the glob" in new SiteDirectory with MockS3 {
      implicit val site = buildSite(defaultConfig.copy(max_age = Some(Right(Map("assets/**/*.js" -> 90)))), files = "assets/lib/jquery.js" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=90")
    }

    "not be applied if the glob doesn't match" in new SiteDirectory with MockS3 {
      implicit val site = buildSite(defaultConfig.copy(max_age = Some(Right(Map("*.js" -> 90)))), files = "index.html" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must beNull
    }
  }

  "redirect in config" should {
    "result in a redirect instruction that is sent to AWS" in new SiteDirectory with MockS3 {
      implicit val site = buildSite(defaultConfig.copy(redirects = Some(Map("index.php" -> "/index.html"))))
      Push.pushSite
      sentPutObjectRequest.getRedirectLocation must equalTo("/index.html")
    }
  }

  "dotfiles" should {
    "included in the pushed files" in new SiteDirectory with MockS3 {
      implicit val site = buildSite(files = ".vimrc" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo(".vimrc")
    }
  }

  trait MockS3 extends Scope {
    val amazonS3Client = mock(classOf[AmazonS3])
    implicit val s3ClientProvider: S3ClientProvider = _ => amazonS3Client
    when(amazonS3Client.listObjects(Matchers.any(classOf[ListObjectsRequest]))).thenReturn {
      val listing = new ObjectListing
      //listing.getObjectSummaries.add(new ObjectSummary())
      listing
    }
    val s3 = new S3()

    def asSeenByS3Client(upload: Upload)(implicit config: Config): PutObjectRequest = {
      Await.ready(s3.upload(upload withUploadType NewFile), Duration("1 s"))
      val req = ArgumentCaptor.forClass(classOf[PutObjectRequest])
      verify(amazonS3Client).putObject(req.capture())
      req.getValue
    }

    def sentPutObjectRequests: Seq[PutObjectRequest] = {
      val req = ArgumentCaptor.forClass(classOf[PutObjectRequest])
      verify(amazonS3Client).putObject(req.capture())
      req.getAllValues
    }

    def sentPutObjectRequest = sentPutObjectRequests.ensuring(_.length == 1).head
  }
  
  trait SiteDirectory extends After {
    val siteDir = new File(FileUtils.getTempDirectory, "site" + Random.nextLong())
    siteDir.mkdir()

    def after {
      FileUtils.forceDelete(siteDir)
    }

    def buildSite(config: Config): Site = Site(siteDir.getAbsolutePath, config)

    def buildSite(config: Config = defaultConfig, files: Seq[String]): Site = {
      files.foreach { file =>
        FileUtils.forceMkdir(new File(siteDir, file).getParentFile)
        new File(siteDir, file).createNewFile()
      }
      buildSite(config)
    }
  }

  class TempFile(val fileContents: String = "<html></html>") extends Scope {
    val tempFile = createTempFile("test", "file")
    tempFile.deleteOnExit()
    write(tempFile, fileContents)

    implicit val config: Config = defaultConfig
  }

  val defaultConfig = Config(
    s3_id = "foo",
    s3_secret = "bar",
    s3_bucket = "bucket",
    s3_endpoint = S3Endpoint.defaultEndpoint,
    max_age = None,
    gzip = None,
    gzip_zopfli = None,
    extensionless_mime_type = None,
    ignore_on_server = None,
    exclude_from_upload = None,
    s3_reduced_redundancy = None,
    cloudfront_distribution_id = None,
    cloudfront_invalidate_root = None,
    redirects = None,
    concurrency_level = 1
  )
}
