package s3.website

import s3.website.model._
import s3.website.Ruby.rubyRegexMatches
import s3.website._

object Diff {

  def resolveDeletes(localFiles: Seq[LocalFile], s3Files: Seq[S3File], redirects: Seq[Upload with UploadTypeResolved])(implicit config: Config): Seq[S3File] = {
    val keysNotToBeDeleted: Set[String] = (localFiles ++ redirects).map(_.s3Key).toSet
    s3Files.filterNot { s3File =>
      val ignoreOnServer = config.ignore_on_server.exists(_.fold(
        (ignoreRegex: String)        => rubyRegexMatches(s3File.s3Key, ignoreRegex),
        (ignoreRegexes: Seq[String]) => ignoreRegexes.exists(rubyRegexMatches(s3File.s3Key, _))
      ))
      keysNotToBeDeleted.exists(_ == s3File.s3Key) || ignoreOnServer
    }
  }

  def resolveNewFiles(localFiles: Seq[LocalFile], s3Files: Seq[S3File])(implicit config: Config):
  Stream[Either[ErrorReport, Upload with UploadTypeResolved]] = {
    val remoteS3KeysIndex = s3Files.map(_.s3Key).toSet
    localFiles
      .toStream // Load lazily, because the MD5 computation for the local file requires us to read the whole file
      .map(resolveUploadSource)
      .collect {
      case errorOrUpload if errorOrUpload.right.exists(isNewUpload(remoteS3KeysIndex)) =>
        for (upload <- errorOrUpload.right) yield upload withUploadType NewFile
    }
  }

  def isNewUpload(remoteS3KeysIndex: Set[String])(u: Upload) = !remoteS3KeysIndex.exists(_ == u.s3Key)

  def resolveUploadSource(localFile: LocalFile)(implicit config: Config): Either[ErrorReport, Upload] =
    for (upload <- LocalFile.toUpload(localFile).right)
    yield upload
}
