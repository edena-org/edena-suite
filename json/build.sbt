name := "edena-json"

description := "JSON utils, etc."


resolvers ++= Seq(
  Resolver.mavenLocal
)

val playJsonVersion = "2.7.4" //2.6.10"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % playJsonVersion
)
