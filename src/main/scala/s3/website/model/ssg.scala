package s3.website.model

import java.io.File
import s3.website.{ErrorOrFile, ErrorReport}

// ssg = static site generator
trait Ssg {
  def outputDirectory: String
}

object Ssg {
  val automaticallySupportedSiteGenerators = Jekyll :: Nanoc :: Nil

  val notFoundErrorReport =
    new ErrorReport {
      def reportMessage =
        """|Could not find a website in any of the pre-defined directories.
           |Specify the website location with the --site=path argument and try again.""".stripMargin
    }

  def findSiteDirectory(workingDirectory: File): ErrorOrFile =
    LocalFile.recursiveListFiles(workingDirectory).find { file =>
      file.isDirectory && automaticallySupportedSiteGenerators.exists(_.outputDirectory == file.getName)
    }.fold(Left(notFoundErrorReport): ErrorOrFile)(Right(_))
}

case object Jekyll extends Ssg {
  def outputDirectory = "_site"
}

case object Nanoc extends Ssg {
  def outputDirectory = "public/output"
}