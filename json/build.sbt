import Dependencies.Versions

name := "edena-json"

description := "JSON utils, etc."


resolvers ++= Seq(
  Resolver.mavenLocal
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % Versions.playJson,
  // Test
  "org.scalatest" %% "scalatest" % Versions.scalaTest % "test",
)
