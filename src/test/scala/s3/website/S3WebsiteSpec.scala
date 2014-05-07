package s3.website

import org.specs2.mutable.{After, Specification}
import s3.website.model._
import org.specs2.specification.Scope
import org.apache.commons.io.FileUtils
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import scala.util.Random
import org.mockito.{Mockito, Matchers, ArgumentCaptor}
import org.mockito.Mockito._
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._
import s3.website.S3.{S3Settings, S3ClientProvider}
import scala.collection.JavaConversions._
import s3.website.model.NewFile
import scala.Some
import com.amazonaws.AmazonServiceException
import org.apache.commons.codec.digest.DigestUtils.md5Hex
import s3.website.CloudFront.{CloudFrontSettings, CloudFrontClientProvider}
import com.amazonaws.services.cloudfront.AmazonCloudFront
import com.amazonaws.services.cloudfront.model.{CreateInvalidationResult, CreateInvalidationRequest, TooManyInvalidationsInProgressException}
import org.mockito.stubbing.Answer
import org.mockito.invocation.InvocationOnMock

class S3WebsiteSpec extends Specification {

  "gzip: true" should {
    "update a gzipped S3 object if the contents has changed" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(
        config = defaultConfig.copy(gzip = Some(Left(true))),
        localFilesWithContent = ("styles.css", "<h1>hi again</h1>") :: Nil
      )
      setS3Files(S3File("styles.css", "1c5117e5839ad8fc00ce3c41296255a1" /* md5 of the gzip of the file contents */))
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo("styles.css")
    }

    "not update a gzipped S3 object if the contents has not changed" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(
        config = defaultConfig.copy(gzip = Some(Left(true))),
        localFilesWithContent = ("styles.css", "<h1>hi</h1>") :: Nil
      )
      setS3Files(S3File("styles.css", "1c5117e5839ad8fc00ce3c41296255a1" /* md5 of the gzip of the file contents */))
      Push.pushSite
      noUploadsOccurred must beTrue
    }
  }

  """
    gzip:
      - .xml
  """ should {
    "update a gzipped S3 object if the contents has changed" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(
        config = defaultConfig.copy(gzip = Some(Right(".xml" :: Nil))),
        localFilesWithContent = ("file.xml", "<h1>hi again</h1>") :: Nil
      )
      setS3Files(S3File("file.xml", "1c5117e5839ad8fc00ce3c41296255a1" /* md5 of the gzip of the file contents */))
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo("file.xml")
    }
  }


  "push" should {
    "not upload a file if it has not changed" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(localFilesWithContent = ("index.html", "<div>hello</div>") :: Nil)
      setS3Files(S3File("index.html", md5Hex("<div>hello</div>")))
      Push.pushSite
      noUploadsOccurred must beTrue
    }

    "update a file if it has changed" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(localFilesWithContent = ("index.html", "<h1>old text</h1>") :: Nil)
      setS3Files(S3File("index.html", md5Hex("<h1>new text</h1>")))
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo("index.html")
    }

    "create a file if does not exist on S3" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(localFilesWithContent = ("index.html", "<h1>hello</h1>") :: Nil)
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo("index.html")
    }

    "delete files that are on S3 but not on local file system" in new SiteDirectory with MockAWS {
      implicit val site = buildSite()
      setS3Files(S3File("old.html", md5Hex("<h1>old text</h1>")))
      Push.pushSite
      sentDelete must equalTo("old.html")
    }

    "try again if the upload fails" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(localFilesWithContent = ("index.html", "<h1>hello</h1>") :: Nil)
      uploadFailsAndThenSucceeds(howManyFailures = 5)
      Push.pushSite
      verify(amazonS3Client, times(6)).putObject(Matchers.any(classOf[PutObjectRequest]))
    }

    "try again if the delete fails" in new SiteDirectory with MockAWS {
      implicit val site = buildSite()
      setS3Files(S3File("old.html", md5Hex("<h1>old text</h1>")))
      deleteFailsAndThenSucceeds(howManyFailures = 5)
      Push.pushSite
      verify(amazonS3Client, times(6)).deleteObject(Matchers.anyString(), Matchers.anyString())
    }
  }

  "push with CloudFront" should {
    "invalidate the CloudFront items" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(
        config = defaultConfig.copy(cloudfront_distribution_id = Some("EGM1J2JJX9Z")),
        localFiles = "test.css" :: "articles/index.html" :: Nil
      )
      Push.pushSite
      sentInvalidationRequest.getInvalidationBatch.getPaths.getItems.toSeq.sorted must equalTo(("/test.css" :: "/articles/index.html" :: Nil).sorted)
    }

    "not send CloudFront invalidation requests on redirect objects" in new SiteDirectory with MockAWS {
      implicit val site = buildSite(
        config = defaultConfig.copy(cloudfront_distribution_id = Some("EGM1J2JJX9Z"), redirects = Some(Map("/index.php" -> "index.html")))
      )
      Push.pushSite
      noInvalidationsOccurred must beTrue
    }

    "retry CloudFront responds with TooManyInvalidationsInProgressException" in new SiteDirectory with MockAWS {
      setTooManyInvalidationsInProgress(4)
      implicit val site = siteWithFiles(
        config = defaultConfig.copy(cloudfront_distribution_id = Some("EGM1J2JJX9Z")),
        localFiles = "test.css" :: Nil
      )
      Push.pushSite must equalTo(0) // The retries should finally result in a success
      sentInvalidationRequests.length must equalTo(4)
    }

    "encode unsafe characters in the keys" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(
        config = defaultConfig.copy(cloudfront_distribution_id = Some("EGM1J2JJX9Z")),
        localFiles = "articles/arnold's file.html" :: Nil
      )
      Push.pushSite
      sentInvalidationRequest.getInvalidationBatch.getPaths.getItems.toSeq.sorted must equalTo(("/articles/arnold's%20file.html" :: Nil).sorted)
    }
  }

  "cloudfront_invalidate_root: true" should {
    "convert CloudFront invalidation paths with the '/index.html' suffix into '/'"  in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(
        config = defaultConfig.copy(cloudfront_distribution_id = Some("EGM1J2JJX9Z"), cloudfront_invalidate_root = Some(true)),
        localFiles = "index.html" :: "articles/index.html" :: Nil
      )
      Push.pushSite
      sentInvalidationRequest.getInvalidationBatch.getPaths.getItems.toSeq.sorted must equalTo(("/" :: "/articles/" :: Nil).sorted)
    }
  }

  "a site with over 1000 items" should {
    "split the CloudFront invalidation requests into batches of 1000 items" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(
        config = defaultConfig.copy(cloudfront_distribution_id = Some("EGM1J2JJX9Z")),
        localFiles = (1 to 1002).map { i => s"file-$i"}
      )
      Push.pushSite
      sentInvalidationRequests.length must equalTo(2)
      sentInvalidationRequests(0).getInvalidationBatch.getPaths.getItems.length must equalTo(1000)
      sentInvalidationRequests(1).getInvalidationBatch.getPaths.getItems.length must equalTo(2)
    }
  }

  "push exit status" should {
    "be 0 all uploads succeed" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(localFilesWithContent = ("index.html", "<h1>hello</h1>") :: Nil)
      Push.pushSite must equalTo(0)
    }

    "be 1 if any of the uploads fails" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(localFilesWithContent = ("index.html", "<h1>hello</h1>") :: Nil)
      when(amazonS3Client.putObject(Matchers.any(classOf[PutObjectRequest]))).thenThrow(new AmazonServiceException("AWS failed"))
      Push.pushSite must equalTo(1)
    }

    "be 0 if CloudFront invalidations and uploads succeed"in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(
        config = defaultConfig.copy(cloudfront_distribution_id = Some("EGM1J2JJX9Z")),
        localFiles = "test.css" :: "articles/index.html" :: Nil
      )
      Push.pushSite must equalTo(0)
    }

    "be 1 if CloudFront invalidation fails"in new SiteDirectory with MockAWS {
      setCloudFrontAsInternallyBroken()
      implicit val site = siteWithFiles(
        config = defaultConfig.copy(cloudfront_distribution_id = Some("EGM1J2JJX9Z")),
        localFiles = "test.css" :: "articles/index.html" :: Nil
      )
      Push.pushSite must equalTo(1)
    }

    "be 0 if upload retry succeeds" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(localFilesWithContent = ("index.html", "<h1>hello</h1>") :: Nil)
      uploadFailsAndThenSucceeds(howManyFailures = 1)
      Push.pushSite must equalTo(0)
    }

    "be 1 if delete retry fails" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFilesAndContent(localFilesWithContent = ("index.html", "<h1>hello</h1>") :: Nil)
      uploadFailsAndThenSucceeds(howManyFailures = 6)
      Push.pushSite must equalTo(1)
    }
  }

  "s3_website.yml file" should {
    "never be uploaded" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(localFiles = "s3_website.yml" :: Nil)
      Push.pushSite
      noUploadsOccurred must beTrue
    }
  }

  "exclude_from_upload: string" should {
    "result in matching files not being uploaded" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(
        config = defaultConfig.copy(exclude_from_upload = Some(Left(".DS_.*?"))),
        localFiles = ".DS_Store" :: Nil
      )
      Push.pushSite
      noUploadsOccurred must beTrue
    }
  }

  """
     exclude_from_upload:
       - regex
       - another_exclusion
  """ should {
    "result in matching files not being uploaded" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(
        config = defaultConfig.copy(exclude_from_upload = Some(Right(".DS_.*?" :: "logs" :: Nil))),
        localFiles = ".DS_Store" :: "logs/test.log" :: Nil
      )
      Push.pushSite
      noUploadsOccurred must beTrue
    }
  }

  "ignore_on_server: value" should {
    "not delete the S3 objects that match the ignore value" in new SiteDirectory with MockAWS {
      implicit val site = buildSite(config = defaultConfig.copy(ignore_on_server = Some(Left("logs"))))
      setS3Files(S3File("logs/log.txt", ""))
      Push.pushSite
      noDeletesOccurred must beTrue
    }
  }

  """
     ignore_on_server:
       - regex
       - another_ignore
  """ should {
    "not delete the S3 objects that match the ignore value" in new SiteDirectory with MockAWS {
      implicit val site = buildSite(config = defaultConfig.copy(ignore_on_server = Some(Right(".*txt" :: Nil))))
      setS3Files(S3File("logs/log.txt", ""))
      Push.pushSite
      noDeletesOccurred must beTrue
    }
  }

  "max-age in config" can {
    "be applied to all files" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(defaultConfig.copy(max_age = Some(Left(60))), localFiles = "index.html" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=60")
    }

    "be applied to files that match the glob" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(defaultConfig.copy(max_age = Some(Right(Map("*.html" -> 90)))), localFiles = "index.html" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=90")
    }

    "be applied to directories that match the glob" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(defaultConfig.copy(max_age = Some(Right(Map("assets/**/*.js" -> 90)))), localFiles = "assets/lib/jquery.js" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=90")
    }

    "not be applied if the glob doesn't match" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(defaultConfig.copy(max_age = Some(Right(Map("*.js" -> 90)))), localFiles = "index.html" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must beNull
    }
  }

  "s3_reduced_redundancy: true in config" should {
    "result in uploads being marked with reduced redundancy" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(defaultConfig.copy(s3_reduced_redundancy = Some(true)), localFiles = "index.html" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getStorageClass must equalTo("REDUCED_REDUNDANCY")
    }
  }

  "s3_reduced_redundancy: false in config" should {
    "result in uploads being marked with the default storage class" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(defaultConfig.copy(s3_reduced_redundancy = Some(false)), localFiles = "index.html" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getStorageClass must beNull
    }
  }

  "redirect in config" should {
    "result in a redirect instruction that is sent to AWS" in new SiteDirectory with MockAWS {
      implicit val site = buildSite(defaultConfig.copy(redirects = Some(Map("index.php" -> "/index.html"))))
      Push.pushSite
      sentPutObjectRequest.getRedirectLocation must equalTo("/index.html")
    }

    "result in max-age=0 Cache-Control header on the object" in new SiteDirectory with MockAWS {
      implicit val site = buildSite(defaultConfig.copy(redirects = Some(Map("index.php" -> "/index.html"))))
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=0, no-cache")
    }
  }

  "redirect in config and an object on the S3 bucket" should {
    "not result in the S3 object being deleted" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(
        localFiles = "index.html" :: Nil,
        config = defaultConfig.copy(redirects = Some(Map("index.php" -> "/index.html")))
      )
      setS3Files(S3File("index.php", "md5"))
      Push.pushSite
      noDeletesOccurred must beTrue
    }
  }

  "dotfiles" should {
    "be included in the pushed files" in new SiteDirectory with MockAWS {
      implicit val site = siteWithFiles(localFiles = ".vimrc" :: Nil)
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo(".vimrc")
    }
  }
  
  trait MockAWS extends MockS3 with MockCloudFront with Scope
  
  trait MockCloudFront {
    val amazonCloudFrontClient = mock(classOf[AmazonCloudFront])
    implicit val cfSettings: CloudFrontSettings = CloudFrontSettings(
      cfClient = _ => amazonCloudFrontClient,
      cloudFrontSleepTimeUnit = MICROSECONDS
    )

    def sentInvalidationRequests: Seq[CreateInvalidationRequest] = {
      val createInvalidationReq = ArgumentCaptor.forClass(classOf[CreateInvalidationRequest])
      verify(amazonCloudFrontClient, Mockito.atLeastOnce()).createInvalidation(createInvalidationReq.capture())
      createInvalidationReq.getAllValues
    }

    def sentInvalidationRequest = sentInvalidationRequests.ensuring(_.length == 1).head

    def noInvalidationsOccurred = {
      verify(amazonCloudFrontClient, Mockito.never()).createInvalidation(Matchers.any(classOf[CreateInvalidationRequest]))
      true // Mockito is based on exceptions
    }

    def setTooManyInvalidationsInProgress(attemptWhenInvalidationSucceeds: Int) {
      var callCount = 0
      doAnswer(new Answer[CreateInvalidationResult] {
        override def answer(invocation: InvocationOnMock): CreateInvalidationResult = {
          callCount += 1
          if (callCount < attemptWhenInvalidationSucceeds)
            throw new TooManyInvalidationsInProgressException("just too many, man")
          else
            mock(classOf[CreateInvalidationResult])
        }
      }).when(amazonCloudFrontClient).createInvalidation(Matchers.anyObject())
    }

    def setCloudFrontAsInternallyBroken() {
      when(amazonCloudFrontClient.createInvalidation(Matchers.anyObject())).thenThrow(new AmazonServiceException("CloudFront is down"))
    }
  }
  
  trait MockS3 {
    val amazonS3Client = mock(classOf[AmazonS3])
    implicit val s3Settings: S3Settings = S3Settings(
      s3Client = _ => amazonS3Client,
      retrySleepTimeUnit = MICROSECONDS
    )
    val s3ObjectListing = new ObjectListing
    when(amazonS3Client.listObjects(Matchers.any(classOf[ListObjectsRequest]))).thenReturn(s3ObjectListing)

    def setS3Files(s3Files: S3File*) {
      s3Files.foreach { s3File =>
        s3ObjectListing.getObjectSummaries.add({
          val summary = new S3ObjectSummary
          summary.setETag(s3File.md5)
          summary.setKey(s3File.s3Key)
          summary
        })
      }
    }

    val s3 = new S3()

    def uploadFailsAndThenSucceeds(howManyFailures: Int) {
      var callCount = 0
      doAnswer(new Answer[PutObjectResult] {
        override def answer(invocation: InvocationOnMock) = {
          callCount += 1
          if (callCount <= howManyFailures)
            throw new AmazonServiceException("AWS is temporarily down")
          else
            mock(classOf[PutObjectResult])
        }
      }).when(amazonS3Client).putObject(Matchers.anyObject())
    }

    def deleteFailsAndThenSucceeds(howManyFailures: Int) {
      var callCount = 0
      doAnswer(new Answer[DeleteObjectRequest] {
        override def answer(invocation: InvocationOnMock) = {
          callCount += 1
          if (callCount <= howManyFailures)
            throw new AmazonServiceException("AWS is temporarily down")
          else
            mock(classOf[DeleteObjectRequest])
        }
      }).when(amazonS3Client).deleteObject(Matchers.anyString(), Matchers.anyString())
    }

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

    def sentDeletes: Seq[S3Key] = {
      val deleteKey = ArgumentCaptor.forClass(classOf[S3Key])
      verify(amazonS3Client).deleteObject(Matchers.anyString(), deleteKey.capture())
      deleteKey.getAllValues
    }

    def sentDelete = sentDeletes.ensuring(_.length == 1).head

    def noDeletesOccurred = {
      verify(amazonS3Client, never()).deleteObject(Matchers.anyString(), Matchers.anyString())
      true // Mockito is based on exceptions
    }

    def noUploadsOccurred = {
      verify(amazonS3Client, never()).putObject(Matchers.any(classOf[PutObjectRequest]))
      true // Mockito is based on exceptions
    }

    type S3Key = String 
  }
  
  trait SiteDirectory extends After {
    val siteDir = new File(FileUtils.getTempDirectory, "site" + Random.nextLong())
    siteDir.mkdir()

    def after {
      FileUtils.forceDelete(siteDir)
    }

    def buildSite(config: Config = defaultConfig): Site = Site(siteDir.getAbsolutePath, config)

    def siteWithFilesAndContent(config: Config = defaultConfig, localFilesWithContent: Seq[(String, String)]): Site = {
      localFilesWithContent.foreach {
        case (filePath, content) =>
          val file = new File(siteDir, filePath)
          FileUtils.forceMkdir(file.getParentFile)
          file.createNewFile()
          FileUtils.write(file, content)
      }
      buildSite(config)
    }

    def siteWithFiles(config: Config = defaultConfig, localFiles: Seq[String]): Site =
      siteWithFilesAndContent(config, localFilesWithContent = localFiles.map((_, "file contents")))
  }

  val defaultConfig = Config(
    s3_id = "foo",
    s3_secret = "bar",
    s3_bucket = "bucket",
    s3_endpoint = S3Endpoint.defaultEndpoint,
    max_age = None,
    gzip = None,
    gzip_zopfli = None,
    ignore_on_server = None,
    exclude_from_upload = None,
    s3_reduced_redundancy = None,
    cloudfront_distribution_id = None,
    cloudfront_invalidate_root = None,
    redirects = None,
    concurrency_level = 1
  )
}
