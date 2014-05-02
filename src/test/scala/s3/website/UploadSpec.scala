package s3.website

import org.specs2.mutable.Specification
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
import com.amazonaws.services.s3.model.ObjectMetadata
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

class UploadSpec extends Specification {

  "gzip" should {
    "gzip the file" in new TempFile {
      val upload = LocalFile.toUpload(LocalFile("index.html", tempFile, Some(Left(Gzip()))))
      IOUtils.toString(new GZIPInputStream(upload.right.get.openInputStream())) must equalTo(fileContents)
    }

    "calculate the md5 for the gzipped data" in new TempFile {
      val upload = LocalFile.toUpload(LocalFile("index.html", tempFile, Some(Left(Gzip()))))
      upload.right.get.md5 must equalTo("9b0474867213eeedf33dfe055680e7fa")
    }

    "calculate the md5 for the non-gzipped data" in new TempFile {
      val upload = LocalFile.toUpload(LocalFile("index.html", tempFile, None))
      upload.right.get.md5 must equalTo(DigestUtils.md5Hex(fileContents))
    }
  }

  "max-age in config" can {
    "be applied to all files" in new TempFile with MockS3 {
      val upload = LocalFile.toUpload(LocalFile("index.html", tempFile, None))(config.copy(max_age = Some(Left(60))))
      asSeenByS3Client(upload.right.get).getCacheControl must equalTo("max-age=60")
    }

    "be applied to files that match the glob" in new TempFile with MockS3 {
      val upload = LocalFile.toUpload(LocalFile("index.html", tempFile, None))(config.copy(max_age = Some(Right(Map("*.html" -> 90)))))
      asSeenByS3Client(upload.right.get).getCacheControl must equalTo("max-age=90")
    }

    "be applied to directories that match the glob" in new TempFile with MockS3 {
      val upload = LocalFile.toUpload(LocalFile("assets/lib/jquery.js", tempFile, None))(config.copy(max_age = Some(Right(Map("assets/**/*.js" -> 90)))))
      asSeenByS3Client(upload.right.get).getCacheControl must equalTo("max-age=90")
    }

    "not be applied if the glob doesn't match" in new TempFile with MockS3 {
      val upload = LocalFile.toUpload(LocalFile("index.html", tempFile, None))(config.copy(max_age = Some(Right(Map("*.js" -> 90)))))
      asSeenByS3Client(upload.right.get).getCacheControl must beNull
    }
  }

  "#resolveLocalFiles" should {
    "find .dot files" in new TestSite {
      val localFiles = LocalFile.resolveLocalFiles(site).right.get
      localFiles.map(_.s3Key) must contain(".vimrc")
    }
  }

  trait MockS3 extends Scope {
    val amazonS3Client = mock(classOf[AmazonS3])
    val s3 = new S3(_ => amazonS3Client)

    def asSeenByS3Client(upload: Upload)(implicit config: Config): ObjectMetadata = {
      Await.ready(s3.upload(upload withUploadType Left(NewFile())), Duration("1 s"))
      val objectMetadataCaptor = ArgumentCaptor.forClass(classOf[ObjectMetadata])
      verify(amazonS3Client).putObject(Matchers.anyObject(), Matchers.anyObject(), Matchers.anyObject(), objectMetadataCaptor.capture())
      objectMetadataCaptor.getValue
    }
  }
  
  class TestSite extends Scope {
    val siteDir = new File(FileUtils.getTempDirectory, "site" + Random.nextLong())
    siteDir.mkdir()
    FileUtils.forceDeleteOnExit(siteDir)

    val configYaml =
      """
        |s3_id: foo
        |s3_secret: bar
        |s3_bucket: baz
      """.stripMargin
    val configFile = new File(siteDir, "s3_website.yml")
    FileUtils.write(configFile, configYaml)

    val files = ".vimrc" :: Nil

    files.foreach { file => new File(siteDir, file).createNewFile()}

    val site = Site.loadSite(configFile.getAbsolutePath, siteDir.getAbsolutePath).right.get
  }
  
  class TempFile(val fileContents: String = "<html></html>") extends Scope {
    val tempFile = createTempFile("test", "file")
    tempFile.deleteOnExit()
    write(tempFile, fileContents)

    implicit val config: Config = Config(
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
}
