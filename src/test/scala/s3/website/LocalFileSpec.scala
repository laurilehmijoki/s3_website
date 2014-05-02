package s3.website

import org.specs2.mutable.Specification
import s3.website.model.LocalFile
import org.specs2.specification.Scope
import java.io.File.createTempFile
import org.apache.commons.io.FileUtils.write
import s3.website.model.Encoding.Gzip
import org.apache.commons.io.IOUtils
import java.util.zip.GZIPInputStream

class LocalFileSpec extends Specification {

  "#toUploadSource" should {
    "gzip the file" in new TempFile(contents = "<html></html>") {
      val uploadSource = LocalFile.toUploadSource(LocalFile("index.html", tempFile, Some(Left(Gzip()))))
      IOUtils.toString(new GZIPInputStream(uploadSource.right.get.openInputStream())) must equalTo("<html></html>")
    }
  }

  class TempFile(contents: String) extends Scope {
    val tempFile = createTempFile("test", "file")
    tempFile.deleteOnExit()
    write(tempFile, contents)
  }
}
