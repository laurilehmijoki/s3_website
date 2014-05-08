package s3.website

import s3.website.model.Config
import scala.collection.parallel.{ForkJoinTaskSupport, ParSeq}
import scala.concurrent.forkjoin.ForkJoinPool

class Utils(implicit config: Config) {
  def toParSeq[T](seq: Seq[T]): ParSeq[T] = {
    val parallelSeq: ParSeq[T] = seq.par
    parallelSeq.tasksupport_=(new ForkJoinTaskSupport(new ForkJoinPool(config.concurrency_level)))
    parallelSeq
  }
}

object Utils {
  lazy val fibs: Stream[Int] = 0 #:: 1 #:: fibs.zip(fibs.tail).map { n => n._1 + n._2 }
}

object Logger {
  import Rainbow._
  def debug(msg: String) = println(s"[${"debg".cyan}] $msg")
  def info(msg: String) = println(s"[${"info".blue}] $msg")
  def fail(msg: String) = println(s"[${"fail".red}] $msg")

  def info(report: SuccessReport) = println(s"[${"succ".green}] ${report.reportMessage}")
  def info(report: FailureReport) = fail(report.reportMessage)

  def pending(msg: String) = println(s"[${"wait".yellow}] $msg")
}

/**
 * Idea copied from https://github.com/ktoso/scala-rainbow.
 */
object Rainbow {
  implicit class RainbowString(val s: String) extends AnyVal {
    import Console._

    /** Colorize the given string foreground to ANSI black */
    def black = BLACK + s + RESET
    /** Colorize the given string foreground to ANSI red */
    def red = RED + s + RESET
    /** Colorize the given string foreground to ANSI red */
    def green = GREEN + s + RESET
    /** Colorize the given string foreground to ANSI red */
    def yellow = YELLOW + s + RESET
    /** Colorize the given string foreground to ANSI red */
    def blue = BLUE + s + RESET
    /** Colorize the given string foreground to ANSI red */
    def magenta = MAGENTA + s + RESET
    /** Colorize the given string foreground to ANSI red */
    def cyan = CYAN + s + RESET
    /** Colorize the given string foreground to ANSI red */
    def white = WHITE + s + RESET

    /** Colorize the given string background to ANSI red */
    def onBlack = BLACK_B + s + RESET
    /** Colorize the given string background to ANSI red */
    def onRed = RED_B+ s + RESET
    /** Colorize the given string background to ANSI red */
    def onGreen = GREEN_B+ s + RESET
    /** Colorize the given string background to ANSI red */
    def onYellow = YELLOW_B + s + RESET
    /** Colorize the given string background to ANSI red */
    def onBlue = BLUE_B+ s + RESET
    /** Colorize the given string background to ANSI red */
    def onMagenta = MAGENTA_B + s + RESET
    /** Colorize the given string background to ANSI red */
    def onCyan = CYAN_B+ s + RESET
    /** Colorize the given string background to ANSI red */
    def onWhite = WHITE_B+ s + RESET

    /** Make the given string bold */
    def bold = BOLD + s + RESET
    /** Underline the given string */
    def underlined = UNDERLINED + s + RESET
    /** Make the given string blink (some terminals may turn this off) */
    def blink = BLINK + s + RESET
    /** Reverse the ANSI colors of the given string */
    def reversed = REVERSED + s + RESET
    /** Make the given string invisible using ANSI color codes */
    def invisible = INVISIBLE + s + RESET
  }
}