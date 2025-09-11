import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}
import Dependencies.Versions

name := "edena-play"

description := "Edena extension for Play Framework providing basic readonly/crud controllers, deadbolt-backed security, json formatters, etc."

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.mavenCentral
)

libraryDependencies += guice

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % Versions.play,
  "be.objectify" %% "deadbolt-scala" % Versions.deadbolt, // Deadbolt (authentication)
  "org.webjars" %% "webjars-play" % Versions.webjars,  // Temporarily disabled due to WebJarExtractor issue
  "org.webjars" % "webjars-locator-core" % "0.59",      // WebJar extractor
  "org.webjars" % "bootstrap" % "3.3.7",                // Bootstrap
  "org.webjars" % "bootswatch-united" % "3.3.4+1"       // Bootstrap
)

// some of the libs' licenses are not included hence we need to provide them (override) manually
licenseOverrides := {
  case
    DepModuleInfo("net.java.dev.jna", "jna", _) // both jna and jna-platform libs have a dual LGPL / Apache 2.0 license, we choose Apache 2.0
    | DepModuleInfo("net.java.dev.jna", "jna-platform", _)
    | DepModuleInfo("org.apache.commons", _, _)
    | DepModuleInfo("commons-codec", "commons-codec", _)
    | DepModuleInfo("commons-io", "commons-io", _)
    | DepModuleInfo("commons-logging", "commons-logging", _)
    | DepModuleInfo("com.google.guava", "guava", _)
    | DepModuleInfo("com.google.inject", "guice", _)
    | DepModuleInfo("com.google.inject.extensions", _, _)
    | DepModuleInfo("cglib", "cglib-nodep", _)
    | DepModuleInfo("org.webjars", "bootswatch-united", "3.3.4+1") =>
  LicenseInfo(LicenseCategory.Apache, "Apache License v2.0", "http://www.apache.org/licenses/LICENSE-2.0")

  // javax.transaction has a dual GPL2 / CDDL license, we choose CDDL
  case DepModuleInfo("javax.transaction", "jta", "1.1") =>
    LicenseInfo(LicenseCategory.CDDL, "Common Development and Distribution License", "https://oss.oracle.com/licenses/CDDL+GPL-1.1")

  // logback libs have a dual LGPL / EPL license, we choose EPL
  case DepModuleInfo("ch.qos.logback", "logback-classic", _)
    | DepModuleInfo("ch.qos.logback", "logback-core", _) 
    | DepModuleInfo("junit", "junit", "4.12") =>
  LicenseInfo(LicenseCategory.EPL, "Eclipse Public License 1.0", "http://www.eclipse.org/legal/epl-v10.html")

  case DepModuleInfo("org.hamcrest", "hamcrest-core", "1.3") =>
    LicenseInfo(LicenseCategory.BSD, "BSD 2-clause", "https://opensource.org/licenses/BSD-2-Clause")

  case DepModuleInfo("org.slf4j", "slf4j-api", "1.7.21") =>
    LicenseInfo(LicenseCategory.MIT, "MIT License", "http://www.opensource.org/licenses/mit-license.php")
}