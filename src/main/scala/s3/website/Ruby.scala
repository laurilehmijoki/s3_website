package s3.website

object Ruby {
  lazy val rubyRuntime = org.jruby.Ruby.newInstance() // Instantiate heavy object
}
