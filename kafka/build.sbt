import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}
import Dependencies.Versions

name := "edena-kafka"

resolvers ++= Seq(
  Resolver.mavenLocal,
  "Confluent Maven Repo" at "https://packages.confluent.io/maven/"
)

libraryDependencies ++= Seq(
  "org.apache.kafka" % "kafka-clients" % "7.5.1-ce",
  "com.typesafe.akka" %% "akka-stream-kafka" % "2.1.1" exclude ("org.apache.kafka", "kafka-clients"), // uses akka streams 2.6.15

//  "com.github.pureconfig" %% "pureconfig" % "0.17.8",

//  "com.sksamuel.avro4s" %% "avro4s-core" % "4.1.1",
  "io.confluent" % "kafka-avro-serializer" % "7.5.1",
  "io.confluent" % "kafka-json-schema-serializer" % "7.5.1",
//  "io.confluent" % "kafka-json-serializer" % "7.5.1",
//  "io.confluent" % "kafka-schema-registry-client" % "7.5.1",

  // TODO: add to jacksonLibs in Dependencies.scala
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jackson,

  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.4.14", // requires JDK11, in order to use JDK8 switch to 1.3.5

  "org.scalatest" %% "scalatest" % "3.2.18" % Test
)
