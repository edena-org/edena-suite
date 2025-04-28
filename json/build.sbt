name := "edena-json"
import Dependencies.Versions

description := "JSON utils, etc."


resolvers ++= Seq(
  Resolver.mavenLocal
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % Versions.playJson
)
