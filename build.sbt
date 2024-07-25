name := "edena-suite"

organization in ThisBuild := "org.edena"
scalaVersion in ThisBuild := "2.12.15"
version in ThisBuild := "0.9.5"
isSnapshot in ThisBuild := false

lazy val core = (project in file("core"))

lazy val json = (project in file("json"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val storeJson = (project in file("store-json"))
  .dependsOn(json)
  .aggregate(json)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val elastic = (project in file("elastic"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val elasticJson = (project in file("elastic-json"))
  .dependsOn(storeJson, elastic)
  .aggregate(storeJson, elastic)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val mongo = (project in file("mongo"))
  .dependsOn(storeJson)
  .aggregate(storeJson)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val ignite = (project in file("ignite"))
  .dependsOn(json)
  .aggregate(json)
  .settings(
      aggregate in test := false,
      aggregate in testOnly := false
  )

lazy val mlSpark = (project in file("ml-spark"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val mlDl4j = (project in file("ml-dl4j"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val ws = (project in file("ws"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val play = (project in file("play"))
  .enablePlugins(PlayScala)
  .dependsOn(json)
  .aggregate(json)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val elasticUtil = (project in file("elastic-util"))
  .dependsOn(ws)
  .aggregate(ws)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val adaServer = (project in file("ada-server"))
  .dependsOn(elasticJson, mongo, ignite, mlSpark)
  .aggregate(elasticJson, mongo, ignite, mlSpark)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

lazy val adaWeb = (project in file("ada-web"))
  .enablePlugins(PlayScala, SbtWeb)
  .dependsOn(play, adaServer)
  .aggregate(play, adaServer, ws, elasticUtil, mlDl4j)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false
  )

fork in Test := true




// POM settings for Sonatype
ThisBuild / homepage := Some(url("https://peterbanda.net"))

ThisBuild / sonatypeProfileName := "org.edena"

ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/edena-org/edena-suite"), "scm:git@github.com:edena-org/edena-suite.git")
)

ThisBuild / developers := List(
  Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net"))
)

//ThisBuild / publishTo := Some(
//  if (isSnapshot.value)
//    Opts.resolver.sonatypeSnapshots
//  else
//    Opts.resolver.sonatypeStaging
//)

ThisBuild / publishTo := sonatypePublishToBundle.value

ThisBuild / publishMavenStyle := true

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

ThisBuild / licenses += "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")