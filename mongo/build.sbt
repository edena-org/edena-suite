name := "edena-store-mongo"

description := "Provides a convenient access layer for Mongo based on ReactiveMongo library."

val reactivemongoVersion = "1.1.0-RC12"
// val reactivemongoPlayVersion = "1.1.0.play26-RC12"
val reactivemongoPlayVersion = "1.1.0.play27-RC12"

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo" % reactivemongoVersion exclude("org.slf4j", "slf4j-api"),
  "org.reactivemongo" %% "reactivemongo-akkastream" % reactivemongoVersion exclude("org.slf4j", "slf4j-api"), // uses akka streams 2.5.32
  "org.reactivemongo" %% "reactivemongo-play-json-compat" % reactivemongoPlayVersion exclude("org.slf4j", "slf4j-api"),

  "org.slf4j" % "slf4j-api" % "1.7.21",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"                // testing
)

dependencyOverrides ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.21"
)