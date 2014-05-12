package s3.website

import org.specs2.mutable.{After, Specification}
import s3.website.model._
import org.specs2.specification.Scope
import org.apache.commons.io.FileUtils
import java.io.File
import scala.util.Random
import org.mockito.{Mockito, Matchers, ArgumentCaptor}
import org.mockito.Mockito._
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import s3.website.S3.S3Setting
import scala.collection.JavaConversions._
import com.amazonaws.AmazonServiceException
import org.apache.commons.codec.digest.DigestUtils.md5Hex
import s3.website.CloudFront.CloudFrontSetting
import com.amazonaws.services.cloudfront.AmazonCloudFront
import com.amazonaws.services.cloudfront.model.{CreateInvalidationResult, CreateInvalidationRequest, TooManyInvalidationsInProgressException}
import org.mockito.stubbing.Answer
import org.mockito.invocation.InvocationOnMock
import java.util.concurrent.atomic.AtomicInteger
import org.apache.commons.io.FileUtils.write
import scala.collection.mutable

class S3WebsiteSpec extends Specification {

  "gzip: true" should {
    "update a gzipped S3 object if the contents has changed" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "gzip: true"
      setLocalFileWithContent(("styles.css", "<h1>hi again</h1>"))
      setS3Files(S3File("styles.css", "1c5117e5839ad8fc00ce3c41296255a1" /* md5 of the gzip of the file contents */))
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo("styles.css")
    }

    "not update a gzipped S3 object if the contents has not changed" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "gzip: true"
      setLocalFileWithContent(("styles.css", "<h1>hi</h1>"))
      setS3Files(S3File("styles.css", "1c5117e5839ad8fc00ce3c41296255a1" /* md5 of the gzip of the file contents */))
      Push.pushSite
      noUploadsOccurred must beTrue
    }
  }

  """
    gzip:
      - .xml
  """ should {
    "update a gzipped S3 object if the contents has changed" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |gzip:
        |  - .xml
      """.stripMargin
      setLocalFileWithContent(("file.xml", "<h1>hi again</h1>"))
      setS3Files(S3File("file.xml", "1c5117e5839ad8fc00ce3c41296255a1" /* md5 of the gzip of the file contents */))
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo("file.xml")
    }
  }

  "push" should {
    "not upload a file if it has not changed" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFileWithContent(("index.html", "<div>hello</div>"))
      setS3Files(S3File("index.html", md5Hex("<div>hello</div>")))
      Push.pushSite
      noUploadsOccurred must beTrue
    }

    "update a file if it has changed" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFileWithContent(("index.html", "<h1>old text</h1>"))
      setS3Files(S3File("index.html", md5Hex("<h1>new text</h1>")))
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo("index.html")
    }

    "create a file if does not exist on S3" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile("index.html")
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo("index.html")
    }

    "delete files that are on S3 but not on local file system" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setS3Files(S3File("old.html", md5Hex("<h1>old text</h1>")))
      Push.pushSite
      sentDelete must equalTo("old.html")
    }

    "try again if the upload fails" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile("index.html")
      uploadFailsAndThenSucceeds(howManyFailures = 5)
      Push.pushSite
      verify(amazonS3Client, times(6)).putObject(Matchers.any(classOf[PutObjectRequest]))
    }

    "not try again if the upload fails on because of invalid credentials" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile("index.html")
      when(amazonS3Client.putObject(Matchers.any(classOf[PutObjectRequest]))).thenThrow {
        val e = new AmazonServiceException("your credentials are incorrect")
        e.setStatusCode(403)
        e
      }
      Push.pushSite
      verify(amazonS3Client, times(1)).putObject(Matchers.any(classOf[PutObjectRequest]))
    }

    "try again if the delete fails" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setS3Files(S3File("old.html", md5Hex("<h1>old text</h1>")))
      deleteFailsAndThenSucceeds(howManyFailures = 5)
      Push.pushSite
      verify(amazonS3Client, times(6)).deleteObject(Matchers.anyString(), Matchers.anyString())
    }

    "try again if the object listing fails" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setS3Files(S3File("old.html", md5Hex("<h1>old text</h1>")))
      objectListingFailsAndThenSucceeds(howManyFailures = 5)
      Push.pushSite
      verify(amazonS3Client, times(6)).listObjects(Matchers.any(classOf[ListObjectsRequest]))
    }
  }

  "push with CloudFront" should {
    "invalidate the updated CloudFront items" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "cloudfront_distribution_id: EGM1J2JJX9Z"
      setLocalFiles("css/test.css", "articles/index.html")
      setOutdatedS3Keys("css/test.css", "articles/index.html")
      Push.pushSite
      sentInvalidationRequest.getInvalidationBatch.getPaths.getItems.toSeq.sorted must equalTo(("/css/test.css" :: "/articles/index.html" :: Nil).sorted)
    }

    "not send CloudFront invalidation requests on new objects"  in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "cloudfront_distribution_id: EGM1J2JJX9Z"
      setLocalFile("newfile.js")
      Push.pushSite
      noInvalidationsOccurred must beTrue
    }

    "not send CloudFront invalidation requests on redirect objects" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |cloudfront_distribution_id: EGM1J2JJX9Z
        |redirects:
        |  /index.php: index.html
      """.stripMargin
      Push.pushSite
      noInvalidationsOccurred must beTrue
    }

    "retry CloudFront responds with TooManyInvalidationsInProgressException" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setTooManyInvalidationsInProgress(4)
      config = "cloudfront_distribution_id: EGM1J2JJX9Z"
      setLocalFile("test.css")
      setOutdatedS3Keys("test.css")
      Push.pushSite must equalTo(0) // The retries should finally result in a success
      sentInvalidationRequests.length must equalTo(4)
    }

    "retry if CloudFront is temporarily unreachable" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      invalidationsFailAndThenSucceed(5)
      config = "cloudfront_distribution_id: EGM1J2JJX9Z"
      setLocalFile("test.css")
      setOutdatedS3Keys("test.css")
      Push.pushSite
      sentInvalidationRequests.length must equalTo(6)
    }

    "encode unsafe characters in the keys" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "cloudfront_distribution_id: EGM1J2JJX9Z"
      setLocalFile("articles/arnold's file.html")
      setOutdatedS3Keys("articles/arnold's file.html")
      Push.pushSite
      sentInvalidationRequest.getInvalidationBatch.getPaths.getItems.toSeq.sorted must equalTo(("/articles/arnold's%20file.html" :: Nil).sorted)
    }

    "invalidate the root object '/' if a top-level object is updated or deleted" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "cloudfront_distribution_id: EGM1J2JJX9Z"
      setLocalFile("maybe-index.html")
      setOutdatedS3Keys("maybe-index.html")
      Push.pushSite
      sentInvalidationRequest.getInvalidationBatch.getPaths.getItems.toSeq.sorted must equalTo(("/" :: "/maybe-index.html" :: Nil).sorted)
    }
  }

  "cloudfront_invalidate_root: true" should {
    "convert CloudFront invalidation paths with the '/index.html' suffix into '/'"  in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |cloudfront_distribution_id: EGM1J2JJX9Z
        |cloudfront_invalidate_root: true
      """.stripMargin
      setLocalFile("articles/index.html")
      setOutdatedS3Keys("articles/index.html")
      Push.pushSite
      sentInvalidationRequest.getInvalidationBatch.getPaths.getItems.toSeq.sorted must equalTo(("/articles/" :: Nil).sorted)
    }
  }

  "a site with over 1000 items" should {
    "split the CloudFront invalidation requests into batches of 1000 items" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      val files = (1 to 1002).map { i => s"lots-of-files/file-$i"}
      config = "cloudfront_distribution_id: EGM1J2JJX9Z"
      setLocalFiles(files:_*)
      setOutdatedS3Keys(files:_*)
      Push.pushSite
      sentInvalidationRequests.length must equalTo(2)
      sentInvalidationRequests(0).getInvalidationBatch.getPaths.getItems.length must equalTo(1000)
      sentInvalidationRequests(1).getInvalidationBatch.getPaths.getItems.length must equalTo(2)
    }
  }

  "push exit status" should {
    "be 0 all uploads succeed" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFiles("file.txt")
      Push.pushSite must equalTo(0)
    }

    "be 1 if any of the uploads fails" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFiles("file.txt")
      when(amazonS3Client.putObject(Matchers.any(classOf[PutObjectRequest]))).thenThrow(new AmazonServiceException("AWS failed"))
      Push.pushSite must equalTo(1)
    }

    "be 1 if any of the redirects fails" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |redirects:
        |  index.php: /index.html
      """.stripMargin
      when(amazonS3Client.putObject(Matchers.any(classOf[PutObjectRequest]))).thenThrow(new AmazonServiceException("AWS failed"))
      Push.pushSite must equalTo(1)
    }

    "be 0 if CloudFront invalidations and uploads succeed"in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "cloudfront_distribution_id: EGM1J2JJX9Z"
      setLocalFile("test.css")
      setOutdatedS3Keys("test.css")
      Push.pushSite must equalTo(0)
    }

    "be 1 if CloudFront is unreachable or broken"in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setCloudFrontAsInternallyBroken()
      config = "cloudfront_distribution_id: EGM1J2JJX9Z"
      setLocalFile("test.css")
      setOutdatedS3Keys("test.css")
      Push.pushSite must equalTo(1)
    }

    "be 0 if upload retry succeeds" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile("index.html")
      uploadFailsAndThenSucceeds(howManyFailures = 1)
      Push.pushSite must equalTo(0)
    }

    "be 1 if delete retry fails" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile("index.html")
      uploadFailsAndThenSucceeds(howManyFailures = 6)
      Push.pushSite must equalTo(1)
    }

    "be 1 if an object listing fails" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setS3Files(S3File("old.html", md5Hex("<h1>old text</h1>")))
      objectListingFailsAndThenSucceeds(howManyFailures = 6)
      Push.pushSite must equalTo(1)
    }
  }

  "s3_website.yml file" should {
    "never be uploaded" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile("s3_website.yml")
      Push.pushSite
      noUploadsOccurred must beTrue
    }
  }

  "exclude_from_upload: string" should {
    "result in matching files not being uploaded" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "exclude_from_upload: .DS_.*?"
      setLocalFile(".DS_Store")
      Push.pushSite
      noUploadsOccurred must beTrue
    }
  }

  """
     exclude_from_upload:
       - regex
       - another_exclusion
  """ should {
    "result in matching files not being uploaded" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |exclude_from_upload:
        |  - .DS_.*?
        |  - logs
      """.stripMargin
      setLocalFiles(".DS_Store", "logs/test.log")
      Push.pushSite
      noUploadsOccurred must beTrue
    }
  }

  "ignore_on_server: value" should {
    "not delete the S3 objects that match the ignore value" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "ignore_on_server: logs"
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
    "not delete the S3 objects that match the ignore value" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |ignore_on_server:
        |  - .*txt
      """.stripMargin
      setS3Files(S3File("logs/log.txt", ""))
      Push.pushSite
      noDeletesOccurred must beTrue
    }
  }

  "max-age in config" can {
    "be applied to all files" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "max_age: 60"
      setLocalFile("index.html")
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=60")
    }

    "be applied to files that match the glob" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |max_age:
        |  "*.html": 90
      """.stripMargin
      setLocalFile("index.html")
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=90")
    }

    "be applied to directories that match the glob" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |max_age:
        |  "assets/**/*.js": 90
      """.stripMargin
      setLocalFile("assets/lib/jquery.js")
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=90")
    }

    "not be applied if the glob doesn't match" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |max_age:
        |  "*.js": 90
      """.stripMargin
      setLocalFile("index.html")
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must beNull
    }

    "be used to disable caching" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "max_age: 0"
      setLocalFile("index.html")
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("no-cache; max-age=0")
    }
  }

  "max-age in config" should {
    "respect the more specific glob" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |max_age:
        |  "assets/*": 150
        |  "assets/*.gif": 86400
      """.stripMargin
      setLocalFiles("assets/jquery.js", "assets/picture.gif")
      Push.pushSite
      sentPutObjectRequests.find(_.getKey == "assets/jquery.js").get.getMetadata.getCacheControl must equalTo("max-age=150")
      sentPutObjectRequests.find(_.getKey == "assets/picture.gif").get.getMetadata.getCacheControl must equalTo("max-age=86400")
    }
  }

  "s3_reduced_redundancy: true in config" should {
    "result in uploads being marked with reduced redundancy" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "s3_reduced_redundancy: true"
      setLocalFile("file.exe")
      Push.pushSite
      sentPutObjectRequest.getStorageClass must equalTo("REDUCED_REDUNDANCY")
    }
  }

  "s3_reduced_redundancy: false in config" should {
    "result in uploads being marked with the default storage class" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = "s3_reduced_redundancy: false"
      setLocalFile("file.exe")
      Push.pushSite
      sentPutObjectRequest.getStorageClass must beNull
    }
  }

  "redirect in config" should {
    "result in a redirect instruction that is sent to AWS" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |redirects:
        |  index.php: /index.html
      """.stripMargin
      Push.pushSite
      sentPutObjectRequest.getRedirectLocation must equalTo("/index.html")
    }

    "result in max-age=0 Cache-Control header on the object" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |redirects:
        |  index.php: /index.html
      """.stripMargin
      Push.pushSite
      sentPutObjectRequest.getMetadata.getCacheControl must equalTo("max-age=0, no-cache")
    }
  }

  "redirect in config and an object on the S3 bucket" should {
    "not result in the S3 object being deleted" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |redirects:
        |  index.php: /index.html
      """.stripMargin
      setLocalFile("index.php")
      setS3Files(S3File("index.php", "md5"))
      Push.pushSite
      noDeletesOccurred must beTrue
    }
  }

  "dotfiles" should {
    "be included in the pushed files" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile(".vimrc")
      Push.pushSite
      sentPutObjectRequest.getKey must equalTo(".vimrc")
    }
  }

  "content type inference" should {
    "add charset=utf-8 to all html documents" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile("index.html")
      Push.pushSite
      sentPutObjectRequest.getMetadata.getContentType must equalTo("text/html; charset=utf-8")
    }

    "add charset=utf-8 to all text documents" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile("index.txt")
      Push.pushSite
      sentPutObjectRequest.getMetadata.getContentType must equalTo("text/plain; charset=utf-8")
    }

    "add charset=utf-8 to all json documents" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFile("data.json")
      Push.pushSite
      sentPutObjectRequest.getMetadata.getContentType must equalTo("application/json; charset=utf-8")
    }

    "resolve the content type from file contents" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      setLocalFileWithContent(("index", "<html><body><h1>hi</h1></body></html>"))
      Push.pushSite
      sentPutObjectRequest.getMetadata.getContentType must equalTo("text/html; charset=utf-8")
    }
  }

  "ERB in config file" should {
    "be evaluated"  in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      config = """
        |redirects:
        |<%= ('a'..'f').to_a.map do |t| '  '+t+ ': /'+t+'.html' end.join('\n')%>
      """.stripMargin
      Push.pushSite
      sentPutObjectRequests.length must equalTo(6)
      sentPutObjectRequests.forall(_.getRedirectLocation != null) must beTrue
    }
  }

  "logging" should {
    "print the debug messages when --verbose is defined" in new EmptySite with VerboseLogger with MockAWS with DefaultRunMode {
      Push.pushSite
      logEntries must contain("[debg] Querying S3 files")
    }

    "not print the debug messages by default" in new EmptySite with NonVerboseLogger with MockAWS with DefaultRunMode {
      Push.pushSite
      logEntries.forall(_.contains("[debg]")) must beFalse
    }
  }
  
  "dry run" should {
    "not push updates" in new EmptySite with VerboseLogger with MockAWS with DryRunMode {
      setLocalFileWithContent(("index.html", "<div>new</div>"))
      setS3Files(S3File("index.html", md5Hex("<div>old</div>")))
      Push.pushSite
      noUploadsOccurred must beTrue
    }

    "not push redirects" in new EmptySite with VerboseLogger with MockAWS with DryRunMode {
      config =
        """
          |redirects:
          |  index.php: /index.html
        """.stripMargin
      Push.pushSite
      noUploadsOccurred must beTrue
    }

    "not push deletes" in new EmptySite with VerboseLogger with MockAWS with DryRunMode {
      setS3Files(S3File("index.html", md5Hex("<div>old</div>")))
      Push.pushSite
      noUploadsOccurred must beTrue
    }

    "not push new files" in new EmptySite with VerboseLogger with MockAWS with DryRunMode {
      setLocalFile("index.html")
      Push.pushSite
      noUploadsOccurred must beTrue
    }
    
    "not invalidate files" in new EmptySite with VerboseLogger with MockAWS with DryRunMode {
      config = "cloudfront_invalidation_id: AABBCC"
      setS3Files(S3File("index.html", md5Hex("<div>old</div>")))
      Push.pushSite
      noInvalidationsOccurred must beTrue
    }
  }

  trait DefaultRunMode {
    implicit def pushMode: PushMode = new PushMode {
      def dryRun = false
    }
  }

  trait DryRunMode {
    implicit def pushMode: PushMode = new PushMode {
      def dryRun = true
    }
  }

  trait MockAWS extends MockS3 with MockCloudFront with Scope

  trait VerboseLogger extends LogCapturer {
    implicit val logger: Logger = new Logger(verboseOutput = true, logMessage = captureAndPrint)
  }

  trait NonVerboseLogger extends LogCapturer {
    implicit val logger: Logger = new Logger(verboseOutput = false, logMessage = captureAndPrint)
  }

  trait LogCapturer {
    val logEntries: mutable.Buffer[String] = mutable.Buffer()

    def captureAndPrint(msg: String) {
      logEntries += msg.replaceAll("\u001B\\[[;\\d]*m", "") // Remove ANSI coloring
      println(msg)
    }
  }
  
  trait MockCloudFront extends MockAWSHelper {
    val amazonCloudFrontClient = mock(classOf[AmazonCloudFront])
    implicit val cfSettings: CloudFrontSetting = CloudFrontSetting(
      cfClient = _ => amazonCloudFrontClient,
      retryTimeUnit = MICROSECONDS
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

    def invalidationsFailAndThenSucceed(implicit howManyFailures: Int, callCount: AtomicInteger = new AtomicInteger(0)) {
      doAnswer(temporaryFailure(classOf[CreateInvalidationResult]))
        .when(amazonCloudFrontClient)
        .createInvalidation(Matchers.anyObject())
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
  
  trait MockS3 extends MockAWSHelper {
    val amazonS3Client = mock(classOf[AmazonS3])
    implicit val s3Settings: S3Setting = S3Setting(
      s3Client = _ => amazonS3Client,
      retryTimeUnit = MICROSECONDS
    )
    val s3ObjectListing = new ObjectListing
    when(amazonS3Client.listObjects(Matchers.any(classOf[ListObjectsRequest]))).thenReturn(s3ObjectListing)

    def setOutdatedS3Keys(s3Keys: String*) {
      s3Keys
        .map(key =>
          S3File(key, md5Hex(Random.nextLong().toString)) // Simulate the situation where the file on S3 is outdated (as compared to the local file)
        )
        .foreach (setS3Files(_))
    }

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

    def uploadFailsAndThenSucceeds(implicit howManyFailures: Int, callCount: AtomicInteger = new AtomicInteger(0)) {
      doAnswer(temporaryFailure(classOf[PutObjectResult]))
        .when(amazonS3Client)
        .putObject(Matchers.anyObject())
    }

    def deleteFailsAndThenSucceeds(implicit howManyFailures: Int, callCount: AtomicInteger = new AtomicInteger(0)) {
      doAnswer(temporaryFailure(classOf[DeleteObjectRequest]))
        .when(amazonS3Client)
        .deleteObject(Matchers.anyString(), Matchers.anyString())
    }

    def objectListingFailsAndThenSucceeds(implicit howManyFailures: Int, callCount: AtomicInteger = new AtomicInteger(0)) {
      doAnswer(temporaryFailure(classOf[ObjectListing]))
        .when(amazonS3Client)
        .listObjects(Matchers.any(classOf[ListObjectsRequest]))
    }

    def asSeenByS3Client(upload: Upload)(implicit config: Config, logger: Logger): PutObjectRequest = {
      val req = ArgumentCaptor.forClass(classOf[PutObjectRequest])
      verify(amazonS3Client).putObject(req.capture())
      req.getValue
    }

    def sentPutObjectRequests: Seq[PutObjectRequest] = {
      val req = ArgumentCaptor.forClass(classOf[PutObjectRequest])
      verify(amazonS3Client, Mockito.atLeast(1)).putObject(req.capture())
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

  trait MockAWSHelper {
    def temporaryFailure[T](clazz: Class[T])(implicit callCount: AtomicInteger, howManyFailures: Int) = new Answer[T] {
      def answer(invocation: InvocationOnMock) = {
        callCount.incrementAndGet()
        if (callCount.get() <= howManyFailures)
          throw new AmazonServiceException("AWS is temporarily down")
        else
          mock(clazz)
      }
    }
  }

  trait SiteDirectory extends After {
    val siteDir = new File(FileUtils.getTempDirectory, "site" + Random.nextLong())
    siteDir.mkdir()

    def after {
      FileUtils.forceDelete(siteDir)
    }
  }
  
  trait EmptySite extends SiteDirectory {
    type LocalFileWithContent = (String, String)

    val localFilesWithContent: mutable.Buffer[LocalFileWithContent] = mutable.Buffer()
    def setLocalFile(fileName: String) = setLocalFileWithContent((fileName, ""))
    def setLocalFiles(fileNames: String*) = fileNames foreach setLocalFile
    def setLocalFileWithContent(fileNameAndContent: LocalFileWithContent) = localFilesWithContent += fileNameAndContent
    def setLocalFilesWithContent(fileNamesAndContent: LocalFileWithContent*) = fileNamesAndContent foreach setLocalFileWithContent
    var config = ""
    var baseConfig =
    """
      |s3_id: foo
      |s3_secret: bar
      |s3_bucket: bucket
    """.stripMargin

    implicit lazy val testSite: Site = siteWithFilesAndContent(config, localFilesWithContent)
    implicit def logger: Logger

    def buildSite(
                    config: String = "",
                    baseConfig: String =
                      """
                        |s3_id: foo
                        |s3_secret: bar
                        |s3_bucket: bucket
                      """.stripMargin
                    ): Site = {
      val configFile = new File(siteDir, "s3_website.yml")
      write(configFile,
        s"""
          |$baseConfig
          |$config
        """.stripMargin
      )
      val errorOrSite: Either[ErrorReport, Site] = Site.loadSite(configFile.getAbsolutePath, siteDir.getAbsolutePath)(logger)
      errorOrSite.left.foreach (error => throw new RuntimeException(error.reportMessage))
      errorOrSite.right.get
    }

    def siteWithFilesAndContent(config: String = "", localFilesWithContent: Seq[LocalFileWithContent]): Site = {
      localFilesWithContent.foreach {
        case (filePath, content) =>
          val file = new File(siteDir, filePath)
          FileUtils.forceMkdir(file.getParentFile)
          file.createNewFile()
          write(file, content)
      }
      buildSite(config)
    }
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
