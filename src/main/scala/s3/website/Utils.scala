package s3.website

object Utils {
  lazy val fibs: Stream[Int] = 0 #:: 1 #:: fibs.zip(fibs.tail).map { n => n._1 + n._2 }
}

class Logger(val verboseOutput: Boolean, logMessage: (String) => Unit = println) {
  import Rainbow._
  def debug(msg: String) = if (verboseOutput) log(Debug, msg)
  def info(msg: String) = log(Info, msg)
  def fail(msg: String) = log(Failure, msg)

  def info(report: SuccessReport) = log(Success, report.reportMessage)
  def info(report: FailureReport) = fail(report.reportMessage)

  def pending(msg: String) = log(Wait, msg)

  private def log(logType: LogType, msgRaw: String) {
    val msg = msgRaw.replaceAll("\\n", "\n       ") // Indent new lines, so that they arrange nicely with other log lines
    logMessage(s"[$logType] $msg")
  }

  sealed trait LogType {
    val prefix: String
    override def toString = prefix
  }
  case object Debug extends LogType {
    val prefix = "debg".cyan
  }
  case object Info extends LogType {
    val prefix = "info".blue
  }
  case object Success extends LogType {
    val prefix = "succ".green
  }
  case object Failure extends LogType {
    val prefix = "fail".red
  }
  case object Wait extends LogType {
    val prefix = "wait".yellow
  }
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