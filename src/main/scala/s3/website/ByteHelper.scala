package s3.website

object ByteHelper {

  // Adapted from http://stackoverflow.com/a/3758880/219947
  def humanReadableByteCount(bytes: Long): String = {
    val si: Boolean = true
    val unit: Int = if (si) 1000 else 1024
    if (bytes < unit) {
      bytes + " B"
    } else {
      val exp: Int = (Math.log(bytes) / Math.log(unit)).asInstanceOf[Int]
      val pre: String = (if (si) "kMGTPE" else "KMGTPE").charAt(exp - 1) + (if (si) "" else "i")
      val formatArgs = (bytes / Math.pow(unit, exp)).asInstanceOf[AnyRef] :: pre :: Nil
      String.format("%.1f %sB", formatArgs.toArray:_*)
    }
  }
}
