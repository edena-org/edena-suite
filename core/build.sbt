import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}

name := "edena-core"

description := "Core library for Edena projects containing utility classes, repo interfaces, and shared/common models."

licenses += "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

resolvers ++= Seq(
  Resolver.mavenLocal
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,

  // Guice
  "net.codingwell" %% "scala-guice" % "4.2.11",   // uses guice 4.2.3 (bellow)
  "com.google.inject" % "guice" % "4.2.3" classifier "no_aop",  // no_aop is set due to https://github.com/google/guice/issues/1133 // 4.0.1
  "com.google.inject.extensions" % "guice-assistedinject" % "4.2.3",

  // Akka
  "com.typesafe.akka" %% "akka-stream" % "2.5.17",

  // Commons
  "commons-io" % "commons-io" % "2.6",
  "commons-lang" % "commons-lang" % "2.6",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "joda-time" % "joda-time" % "2.9.9",

  // Test
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",

  // SLF4J
  "org.slf4j" % "slf4j-api" % "1.7.21"
)

dependencyOverrides ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.21"
)

// some of the libs' licenses are not included hence we need to provide them (override) manually
licenseOverrides := {
  case DepModuleInfo("commons-io", "commons-io", _) =>
    LicenseInfo(LicenseCategory.Apache, "Apache License v2.0", "http://www.apache.org/licenses/LICENSE-2.0")
}
