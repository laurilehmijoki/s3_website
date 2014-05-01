package com.github.laurilehmijoki

import com.github.laurilehmijoki.model._

object Diff {
  def resolveDiff(localFiles: Seq[LocalFile], s3Files: Seq[S3File]): Stream[Either[Error, UploadSource]] =
    localFiles
      .toStream // Load lazily, because the MD5 computation for the local file requires us to read the whole file
      .map(resolveUploadSource)
      .collect {
        case uploadSource if !uploadSource.right.exists(onS3(s3Files)) =>
          for (uploadSrc <- uploadSource.right) yield uploadSrc
      }

  def onS3(s3Files: Seq[S3File])(uploadSource: UploadSource) = s3Files exists (compare(uploadSource)(_))
  
  def resolveUploadSource(localFile: LocalFile): Either[Error, UploadSource] =
    for (uploadSource <- LocalFile.toUploadSource(localFile).right)
    yield uploadSource

  def compare(uploadSource: UploadSource)(s3File: S3File): Boolean =
    s3File.s3Key == uploadSource.s3Key && s3File.md5 == uploadSource.md5
}
