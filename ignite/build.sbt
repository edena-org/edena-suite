import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}
import Dependencies.Versions

name := "edena-store-ignite"

description := "Provides a convenient access layer for Apache Ignite."

libraryDependencies ++= Seq(
  "org.apache.ignite" % "ignite-core" % Versions.ignite,
  "org.apache.ignite" % "ignite-spring" % Versions.ignite,
  "org.apache.ignite" % "ignite-indexing" % Versions.ignite,
  "org.apache.ignite" % "ignite-scalar" % Versions.ignite,
  "org.slf4j" % "slf4j-api" % "1.7.21"
)
