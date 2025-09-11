name := "edena-suite"

organization in ThisBuild := "org.edena"
scalaVersion in ThisBuild := "2.13.11" // "2.12.15"
version in ThisBuild := "1.1.0.RC.13"
isSnapshot in ThisBuild := false

// Dependency override groups
val akkaLibs = Seq(
  "com.typesafe.akka" %% "akka-actor"                 % Dependencies.Versions.akka,
  "com.typesafe.akka" %% "akka-stream"                % Dependencies.Versions.akka,
  "com.typesafe.akka" %% "akka-actor-typed"           % Dependencies.Versions.akka,
  "com.typesafe.akka" %% "akka-slf4j"                 % Dependencies.Versions.akka,
  "com.typesafe.akka" %% "akka-serialization-jackson" % Dependencies.Versions.akka,
  "com.typesafe.akka" %% "akka-protobuf-v3"           % Dependencies.Versions.akka,
  "com.typesafe.akka" %% "akka-coordination"          % Dependencies.Versions.akka,
  "com.typesafe.akka" %% "akka-discovery"             % Dependencies.Versions.akka
)

val akkaCoreLibs = Seq(
  "com.typesafe.akka" %% "akka-actor"  % Dependencies.Versions.akka,
  "com.typesafe.akka" %% "akka-stream" % Dependencies.Versions.akka
)

val jacksonLibs = Seq(
  "com.fasterxml.jackson.core"       % "jackson-core"                   % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.core"       % "jackson-databind"               % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.core"       % "jackson-annotations"            % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.datatype"   % "jackson-datatype-jdk8"          % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.datatype"   % "jackson-datatype-jsr310"        % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.datatype"   % "jackson-datatype-joda"          % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.datatype"   % "jackson-datatype-guava"         % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-xml"         % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml"        % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor"        % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.module"     %% "jackson-module-scala"          % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.module"     % "jackson-module-parameter-names" % Dependencies.Versions.jackson,
  "com.fasterxml.jackson.module"     % "jackson-module-paranamer"       % Dependencies.Versions.jackson
)

val playJsonLibs = Seq(
  "com.typesafe.play" %% "play-json" % Dependencies.Versions.playJson
)

val akkaHttpLibs = Seq(
  "com.typesafe.akka" %% "akka-http"      % Dependencies.Versions.akkaHttp,
  "com.typesafe.akka" %% "akka-http-core" % Dependencies.Versions.akkaHttp,
  "com.typesafe.akka" %% "akka-parsing"   % Dependencies.Versions.akkaHttp
)

val playWsLibs = Seq(
  "com.typesafe.play" %% "play-ws-standalone"      % Dependencies.Versions.playWs,
  "com.typesafe.play" %% "play-ahc-ws-standalone"  % Dependencies.Versions.playWs,
  "com.typesafe.play" %% "play-ws-standalone-json" % Dependencies.Versions.playWs
)

val playLibs = Seq(
  "com.typesafe.play" %% "play-akka-http-server" % Dependencies.Versions.play,
  "com.typesafe.play" %% "play-cache"            % Dependencies.Versions.play,
  "com.typesafe.play" %% "play-ehcache"          % Dependencies.Versions.play,
  "com.typesafe.play" %  "play-exceptions"       % Dependencies.Versions.play,
  "com.typesafe.play" %% "play-guice"            % Dependencies.Versions.play,
  "com.typesafe.play" %% "play-logback"          % Dependencies.Versions.play,
  "com.typesafe.play" %% "play-server"           % Dependencies.Versions.play,
  "com.typesafe.play" %% "play-test"             % Dependencies.Versions.play,
//  "com.typesafe.play" %% "filters-helpers"       % Dependencies.Versions.play,
  "com.typesafe.play" %% "play-netty-server"     % Dependencies.Versions.play
)

lazy val core = (project in file("core"))
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= akkaCoreLibs
  )

lazy val json = (project in file("json"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= akkaCoreLibs ++ jacksonLibs ++ playJsonLibs
  )

lazy val storeJson = (project in file("store-json"))
  .dependsOn(json)
  .aggregate(json)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= jacksonLibs ++ playJsonLibs
  )

lazy val elastic = (project in file("elastic"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= akkaCoreLibs ++ jacksonLibs ++ playJsonLibs
  )

lazy val elasticJson = (project in file("elastic-json"))
  .dependsOn(storeJson, elastic)
  .aggregate(storeJson, elastic)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= jacksonLibs ++ playJsonLibs
  )

lazy val mongo = (project in file("mongo"))
  .dependsOn(storeJson)
  .aggregate(storeJson)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= akkaCoreLibs ++ jacksonLibs ++ playJsonLibs
  )

lazy val ignite = (project in file("ignite"))
  .dependsOn(json)
  .aggregate(json)
  .settings(
      aggregate in test := false,
      aggregate in testOnly := false,
      dependencyOverrides ++= jacksonLibs
  )

lazy val mlSpark = (project in file("ml-spark"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= jacksonLibs
  )

lazy val mlDl4j = (project in file("ml-dl4j"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= akkaCoreLibs ++ jacksonLibs
  )

lazy val ws = (project in file("ws"))
  .dependsOn(core)
  .aggregate(core)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= akkaCoreLibs ++ jacksonLibs ++ akkaHttpLibs ++ playWsLibs
  )

lazy val play = (project in file("play"))
  .enablePlugins(PlayScala)
  .dependsOn(json)
  .aggregate(json)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= jacksonLibs ++ playJsonLibs ++ playLibs
  )

lazy val elasticUtil = (project in file("elastic-util"))
  .dependsOn(ws)
  .aggregate(ws)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= akkaCoreLibs ++ jacksonLibs ++ akkaHttpLibs ++ playWsLibs
  )

lazy val adaServer = (project in file("ada-server"))
  .dependsOn(elasticJson, mongo, ignite, mlSpark)
  .aggregate(elasticJson, mongo, ignite, mlSpark)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= akkaLibs ++ jacksonLibs ++ playJsonLibs ++ playWsLibs
  )

lazy val adaWeb = (project in file("ada-web"))
  .enablePlugins(PlayScala, SbtWeb)
  .dependsOn(play, adaServer)
  .aggregate(play, adaServer, ws, elasticUtil, mlDl4j)
  .settings(
    aggregate in test := false,
    aggregate in testOnly := false,
    dependencyOverrides ++= akkaLibs ++ jacksonLibs ++ playJsonLibs ++ akkaHttpLibs ++ playWsLibs ++ playLibs
  )

fork in Test := true

// Global dependency scheme overrides to resolve version conflicts
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-parser-combinators" % VersionScheme.Always,
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
  "com.github.luben" % "zstd-jni" % VersionScheme.Always
)

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