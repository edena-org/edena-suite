import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}
import Dependencies.Versions

name := "edena-ws"

description := "WS client stuff."

resolvers ++= Seq(
  Resolver.mavenLocal
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws-standalone" % Versions.playWs,
  "com.typesafe.play" %% "play-ahc-ws-standalone" % Versions.playWs,
  "com.typesafe.play" %% "play-ws-standalone-json" % Versions.playWs,
//  "com.typesafe.play" %% "play-ws-standalone-xml" % Versions.playWs,

//  "com.typesafe.play" %% "play-ws" % playVersion,                                                            // WS  exclude("commons-logging", "commons-logging")
//  "com.typesafe.play" %% "play-ahc-ws" % playVersion,                                                        // WS AHC

//  "com.google.inject.extensions" % "guice-assistedinject" % "4.2.3" exclude("com.google.inject", "guice"), // already included in edena-core
  "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,                                                            // JSON WS Streaming

  "org.scalatest" %% "scalatest" % Versions.scalaTest % "test"                                                 // testing
)
