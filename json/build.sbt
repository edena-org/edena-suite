name := "edena-json"

description := "JSON utils, etc."

licenses += "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

resolvers ++= Seq(
  Resolver.mavenLocal
)

val playJsonVersion = "2.6.10"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % playJsonVersion
)
