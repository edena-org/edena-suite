import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}
import Dependencies.Versions

name := "edena-store-elastic"

description := "Provides a convenient access layer for Elastic Search based on Elastic4S library."

libraryDependencies ++= Seq(
  "nl.gn0s1s" %% "elastic4s-core" % Versions.elastic4s, // exclude("com.vividsolutions" ,"jts"), // jts is LGPL licensed (up to version 1.14)
  "nl.gn0s1s" %% "elastic4s-client-esjava" % Versions.elastic4s,
  "nl.gn0s1s" %% "elastic4s-client-akka" % Versions.elastic4s,
  //  "nl.gn0s1s" %% "elastic4s-http" % Versions.elastic4s,
  "nl.gn0s1s" %% "elastic4s-http-streams" % Versions.elastic4s,
  "com.sksamuel.exts" %% "exts" % "1.61.1", // Utilities library (Logging, StringOption, etc.)
//  "javax.inject" % "javax.inject" % "1",
  "org.apache.commons" % "commons-lang3" % "3.5",
  "org.slf4j" % "slf4j-api" % "1.7.21",
  "org.scalatest" %% "scalatest" % Versions.scalaTest % "test"               // testing
)

// For licenses not automatically downloaded (need to list them manually)
licenseOverrides := {
  case
    DepModuleInfo("com.carrotsearch", "hppc", "0.7.1")
  | DepModuleInfo("commons-codec", "commons-codec", "1.10")
  | DepModuleInfo("commons-io", "commons-io", "2.6")
  | DepModuleInfo("commons-logging", "commons-logging", "1.1.3")
  | DepModuleInfo("org.apache.commons", "commons-lang3", "3.5")
  | DepModuleInfo("org.apache.logging.log4j", "log4j-api", "2.9.1") =>
    LicenseInfo(LicenseCategory.Apache, "Apache License v2.0", "http://www.apache.org/licenses/LICENSE-2.0")

  case DepModuleInfo("org.slf4j", "slf4j-api", _) =>
    LicenseInfo(LicenseCategory.MIT, "MIT", "http://opensource.org/licenses/MIT")
}
