package s3.website

import s3.website.model._
import s3.website.Ruby.rubyRegexMatches
import scala.concurrent.{Await, Future}
import scala.util.Try
import java.io.File
import scala.util.Failure
import s3.website.model.LocalFileFromDisk
import scala.util.Success
import s3.website.model.LocalFileDatabase.ChangedFile
import scala.concurrent.duration._

object Diff {

  def resolveDiff(s3FilesFuture: Future[Either[ErrorReport, Seq[S3File]]])
                 (implicit site: Site, logger: Logger): Either[ErrorReport, Seq[Either[DbRecord, ChangedFile]]] =
    if (LocalFileDatabase.hasRecords)
      LocalFileDatabase.resolveDiffWithLocalDb
    else
    // Local file database does not exist. Use the Get Bucket Response for checking the new or changed files.
    // Then persist the local files into the db.
      Await.result(s3FilesFuture, 5 minutes).right flatMap { s3Files =>
        Try {
          val s3KeyIndex = s3Files.map(_.s3Key).toSet
          val s3Md5Index = s3Files.map(_.md5).toSet
          val siteFiles = Files.listSiteFiles
          val nameExistsOnS3 = (f: File) => s3KeyIndex contains site.resolveS3Key(f)
          val newFiles = siteFiles
            .filterNot(nameExistsOnS3)
            .map { f => LocalFileFromDisk(f, uploadType = NewFile)}
          val changedFiles =
            siteFiles
              .filter(nameExistsOnS3)
              .map(f => LocalFileFromDisk(f, uploadType = FileUpdate))
              .filterNot(localFile => s3Md5Index contains localFile.md5)
          val unchangedFiles = {
            val newOrChangedFiles = (changedFiles ++ newFiles).map(_.originalFile).toSet
            siteFiles.filterNot(f => newOrChangedFiles contains f)
          }
          val allFiles: Seq[Either[DbRecord, ChangedFile]] = unchangedFiles.map {
            f => Left(DbRecord(f))
          } ++ (changedFiles ++ newFiles).map {
            Right(_)
          }
          LocalFileDatabase persist allFiles
          allFiles
        } match {
          case Success(allFiles) => Right(allFiles)
          case Failure(err) => Left(ErrorReport(err))
        }
      }


  def resolveDeletes(localS3Keys: Seq[String], s3Files: Seq[S3File], redirects: Seq[Redirect])
                    (implicit config: Config, logger: Logger): Seq[S3File] = {
    val keysNotToBeDeleted: Set[String] = (localS3Keys ++ redirects.map(_.s3Key)).toSet
    s3Files.filterNot { s3File =>
      val ignoreOnServer = config.ignore_on_server.exists(_.fold(
        (ignoreRegex: String)        => rubyRegexMatches(s3File.s3Key, ignoreRegex),
        (ignoreRegexes: Seq[String]) => ignoreRegexes.exists(rubyRegexMatches(s3File.s3Key, _))
      ))
      if (ignoreOnServer) logger.debug(s"Ignoring ${s3File.s3Key} on server")
      keysNotToBeDeleted.exists(_ == s3File.s3Key) || ignoreOnServer
    }
  }
}
