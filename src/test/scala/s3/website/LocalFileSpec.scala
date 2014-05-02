package s3.website

import org.specs2.mutable.Specification
import s3.website.model.LocalFile
import org.specs2.specification.Scope
import java.io.File.createTempFile
import org.apache.commons.io.FileUtils.write
import s3.website.model.Encoding.Gzip
import org.apache.commons.io.IOUtils
import java.util.zip.GZIPInputStream
import org.apache.commons.codec.digest.DigestUtils

class LocalFileSpec extends Specification {

  "#toUploadSource" should {
    "gzip the file" in new TempFile(fileContents = "<html></html>") {
      val uploadSource = LocalFile.toUploadSource(LocalFile("index.html", tempFile, Some(Left(Gzip()))))
      IOUtils.toString(new GZIPInputStream(uploadSource.right.get.openInputStream())) must equalTo(fileContents)
    }

    "calculate the md5 for the gzipped data" in new TempFile(fileContents = "<html></html>") {
      val uploadSource = LocalFile.toUploadSource(LocalFile("index.html", tempFile, Some(Left(Gzip()))))
      uploadSource.right.get.md5 must equalTo("9b0474867213eeedf33dfe055680e7fa")
    }

    "calculate the md5 for the non-gzipped data" in new TempFile(fileContents = "<html></html>") {
      val uploadSource = LocalFile.toUploadSource(LocalFile("index.html", tempFile, None))
      uploadSource.right.get.md5 must equalTo(DigestUtils.md5Hex(fileContents))
    }
  }

  class TempFile(val fileContents: String) extends Scope {
    val tempFile = createTempFile("test", "file")
    tempFile.deleteOnExit()
    write(tempFile, fileContents)
  }
}
