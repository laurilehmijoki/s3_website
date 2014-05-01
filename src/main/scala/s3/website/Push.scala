package s3.website

import s3.website.model.LocalFile
import s3.website.model.Site._
import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.duration._
import scala.util.matching.Regex
import com.lexicalscope.jewel.cli.CliFactory
import scala.language.postfixOps

object Push {

  def pushSite(yamlConfigPath: String, siteRootDirectory: String) = {
    val errorOrThreadPool = for {
      loadedSite <- loadSite(yamlConfigPath, siteRootDirectory).right
      s3Files    <- S3.resolveS3Files(loadedSite).right
      localFiles <- LocalFile.resolveLocalFiles(loadedSite).right
    } yield {
      implicit val site = loadedSite
      val threadPool = Executors.newFixedThreadPool(site.config.concurrency_level)
      implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(threadPool)
      val uploadFutures = Diff
        .resolveDiff(localFiles, s3Files)
        .map(_.right.map(S3.upload))
        .par
      uploadFutures.tasksupport_=(new ForkJoinTaskSupport(new ForkJoinPool(site.config.concurrency_level)))
      uploadFutures.map(_.right.foreach { uploadFuture =>
        uploadFuture.map {
          case successOrFailure => successOrFailure match {
            case Right(success) => s"Successfully uploaded ${success.s3Key}"
            case Left(failure)  => s"Failed to upload ${failure.s3Key} (${failure.error.getMessage})"
          }
        } onSuccess {
          case report => println(report)
        }
        Await.ready(uploadFuture, 1 day)
      })
      threadPool
    }
    errorOrThreadPool.right.foreach(_.shutdownNow())
    errorOrThreadPool.left.foreach(error => println(error.message))
  }

  implicit class RegexContext(sc: StringContext) {
    def r = new Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
  }

  trait CliArgs {
    import com.lexicalscope.jewel.cli.Option

    @Option def site: String
    @Option(longName = Array("config-dir")) def configDir: String
    @Option def headless: Boolean // TODO use this

  }

  def main(args: Array[String]) {
    val cliArgs = CliFactory.parseArguments(classOf[CliArgs], args:_*)
    pushSite(
      yamlConfigPath = cliArgs.configDir + "/s3_website.yml",
      siteRootDirectory = cliArgs.site
    )
  }
}
