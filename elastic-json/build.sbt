import Dependencies.Versions

name := "edena-store-elastic-json"

description := "Elastic store JSON stuff"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % Versions.scalaTest % "test",
  "net.codingwell" %% "scala-guice" % "4.2.11" % "test"
)