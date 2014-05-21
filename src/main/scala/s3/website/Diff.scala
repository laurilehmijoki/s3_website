package s3.website

import s3.website.model._
import s3.website.Ruby.rubyRegexMatches
import s3.website._

object Diff {

  def resolveDeletes(localS3Keys: Seq[String], s3Files: Seq[S3File], redirects: Seq[Upload])
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
