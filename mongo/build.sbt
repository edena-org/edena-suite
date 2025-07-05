import Dependencies.Versions

name := "edena-store-mongo"

description := "Provides a convenient access layer for Mongo based on ReactiveMongo library."

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo" % Versions.reactivemongo exclude("org.slf4j", "slf4j-api"),
  "org.reactivemongo" %% "reactivemongo-akkastream" % Versions.reactivemongo exclude("org.slf4j", "slf4j-api"), // uses akka streams 2.5.32
  "org.reactivemongo" %% "reactivemongo-play-json-compat" % Versions.reactivemongoPlay exclude("org.slf4j", "slf4j-api"),

  "org.slf4j" % "slf4j-api" % "1.7.21",
  "org.scalatest" %% "scalatest" % Versions.scalaTest % "test"                // testing
)

dependencyOverrides ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.21"
)