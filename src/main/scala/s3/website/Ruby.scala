package s3.website

object Ruby {
  lazy val rubyRuntime = org.jruby.Ruby.newInstance() // Instantiate heavy object

  def rubyRegexMatches(text: String, regex: String) =
    rubyRuntime.evalScriptlet(
      s"""
         match_data = Regexp.new('$regex').match('$text')
         if match_data
           true
         else
           false
         end
       """
    ).toJava(classOf[Boolean]).asInstanceOf[Boolean]
}
