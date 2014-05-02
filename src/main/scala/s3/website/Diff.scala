package s3.website

import s3.website.model._

object Diff {
  def resolveDiff(localFiles: Seq[LocalFile], s3Files: Seq[S3File]): Stream[Either[Error, UploadSource with UploadType]] ={
    val remoteS3KeysIndex = s3Files.map(_.s3Key).toSet
    val remoteMd5Index = s3Files.map(_.md5).toSet
    localFiles
      .toStream // Load lazily, because the MD5 computation for the local file requires us to read the whole file
      .map(resolveUploadSource)
      .collect {
      case uploadSource if uploadSource.right.exists(isNewUpload(remoteS3KeysIndex)) =>
        for (uploadSrc <- uploadSource.right) yield uploadSrc withUploadType Left(NewFile())
      case uploadSource if uploadSource.right.exists(isUpdate(remoteS3KeysIndex, remoteMd5Index)) =>
        for (uploadSrc <- uploadSource.right) yield uploadSrc withUploadType Right(Update())
    }
  }


  def isNewUpload(remoteS3KeysIndex: Set[String])(u: UploadSource) = !remoteS3KeysIndex.exists(_ == u.s3Key)

  def isUpdate(remoteS3KeysIndex: Set[String], remoteMd5Index: Set[String])(u: UploadSource) =
    remoteS3KeysIndex.exists(_ == u.s3Key) && !remoteMd5Index.exists(_ == u.md5)

  def resolveUploadSource(localFile: LocalFile): Either[Error, UploadSource] =
    for (uploadSource <- LocalFile.toUploadSource(localFile).right)
    yield uploadSource
}
