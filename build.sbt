name := "s3_website_monadic"

version := "0.0.1"

scalaVersion := "2.11.0"

scalacOptions += "-feature"

libraryDependencies += "org.yaml" % "snakeyaml" % "1.13"

libraryDependencies += "org.jruby" % "jruby" % "1.7.11"

libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.7.7"

libraryDependencies += "commons-codec" % "commons-codec" % "1.9"

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "org.apache.tika" % "tika-core" % "1.4"

libraryDependencies += "com.lexicalscope.jewelcli" % "jewelcli" % "0.8.9"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
