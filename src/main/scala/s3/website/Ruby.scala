package s3.website

object Ruby {
  lazy val rubyRuntime = org.jruby.Ruby.newInstance() // Instantiate heavy object

  def rubyRegexMatches(text: String, regex: String) = {
    val z  = rubyRuntime.evalScriptlet(
      s"""# encoding: utf-8
          !!Regexp.new("$regex").match("$text")"""
    )
    z.toJava(classOf[Boolean]).asInstanceOf[Boolean]
  }

}
