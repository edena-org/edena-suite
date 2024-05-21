import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}

name := "edena-store-ignite"

description := "Provides a convenient access layer for Apache Ignite."

licenses += "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

val igniteVersion = "2.4.0"

libraryDependencies ++= Seq(
  "org.apache.ignite" % "ignite-core" % igniteVersion,
  "org.apache.ignite" % "ignite-spring" % igniteVersion,
  "org.apache.ignite" % "ignite-indexing" % igniteVersion,
  "org.apache.ignite" % "ignite-scalar" % igniteVersion,
  "org.slf4j" % "slf4j-api" % "1.7.21"
)
