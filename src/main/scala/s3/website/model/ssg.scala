package s3.website.model

import java.io.File
import s3.website.model.Files.recursiveListFiles

// ssg = static site generator
trait Ssg {
  def outputDirectory: String
}

object Ssg {
  val automaticallySupportedSiteGenerators = Jekyll :: Nanoc :: Middleman :: Nil

  val maxAutodetectDepth = automaticallySupportedSiteGenerators.map(_.outputDirectory).map(_.split(File.separatorChar).length).max

  def autodetectSiteDir(workingDirectory: File): Option[File] =
    recursiveListFiles(maxAutodetectDepth)(workingDirectory).find { file =>
      file.isDirectory && automaticallySupportedSiteGenerators.exists(ssg => file.getAbsolutePath.endsWith(ssg.outputDirectory))
    }
}

case object Jekyll extends Ssg {
  def outputDirectory = "_site"
}

case object Nanoc extends Ssg {
  def outputDirectory = s"public${File.separatorChar}output"
}

case object Middleman extends Ssg {
  def outputDirectory = "build"
}
