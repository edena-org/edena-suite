import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}

name := "edena-ws"

description := "WS client stuff."

licenses += "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

resolvers ++= Seq(
  Resolver.mavenLocal
)

val playVersion = "2.6.20"
val playWsVersion = "1.1.10"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws-standalone" % playWsVersion,
  "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion,
  "com.typesafe.play" %% "play-ws-standalone-json" % playWsVersion,
//  "com.typesafe.play" %% "play-ws-standalone-xml" % playWsVersion,

//  "com.typesafe.play" %% "play-ws" % playVersion,                                                            // WS  exclude("commons-logging", "commons-logging")
//  "com.typesafe.play" %% "play-ahc-ws" % playVersion,                                                        // WS AHC

//  "com.google.inject.extensions" % "guice-assistedinject" % "4.2.3" exclude("com.google.inject", "guice"), // already included in edena-core
  "com.typesafe.akka" %% "akka-http" % "10.0.14",                                                            // JSON WS Streaming

  "org.scalatest" %% "scalatest" % "3.0.1" % "test"                                                          // testing
)
