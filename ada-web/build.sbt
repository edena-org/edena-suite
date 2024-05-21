import PlayKeys._
import com.typesafe.config._
import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}
import com.typesafe.sbt.pgp.PgpKeys._

name := "ada-web"

description := "Web part of Ada Discovery Analytics backed by Play Framework."

licenses += "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

resolvers ++= Seq(
  Resolver.mavenLocal
)

routesImport ++= Seq(
  "reactivemongo.api.bson.BSONObjectID",
  "org.edena.ada.web.controllers.PathBindables._",
  "org.edena.ada.web.controllers.QueryStringBinders._"
)

libraryDependencies ++= Seq(ehcache)

val mongoPlayVersion = "1.1.0.play26-RC12"
val playVersion = "2.6.20"

libraryDependencies ++= Seq(
  "org.pac4j" %% "play-pac4j" % "7.0.1",
  "org.pac4j" % "pac4j-oidc" % "3.8.3",

  "org.reactivemongo" %% "play2-reactivemongo" % mongoPlayVersion,  // Mongo Play
  "com.typesafe.play" %% "play-mailer" % "6.0.1",                   // to send emails
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",             // to send emails (Guice)

  "com.typesafe.play" %% "play-ws" % playVersion,                   // WS to wrap Standalone WS
  "com.typesafe.play" %% "play-ahc-ws" % playVersion,               // WS to wrap Standalone WS

  "org.scalaz" %% "scalaz-core" % "7.2.24",
  "org.webjars" % "typeaheadjs" % "0.11.1",                         // typeahead (autocompletion)
  "org.webjars" % "html5shiv" % "3.7.0",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars.npm" % "bootstrap-select" % "1.13.2",                // bootstrap select element
  "org.webjars.bower" % "plotly.js" % "1.54.1",                     // plotly
  "org.webjars" % "highcharts" % "6.2.0",                           // highcharts
  "org.webjars.bower" % "d3" % "3.5.16",
  "org.webjars.bower" % "Autolinker.js" % "0.25.0",                 // to convert links to a-href elements
  "org.webjars" % "jquery-ui" % "1.11.1",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test",

  // Because of Spark (turning janino logging to warn: https://github.com/janino-compiler/janino/issues/13)
  "ch.qos.logback" % "logback-classic" % "1.2.3"

//  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
//  "ch.qos.logback" % "logback-classic" % "1.2.3",                                                          // to provide slf4j implementation % Runtime
//  "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.14.0"                                                 // to use slf4j instead of log4j
) map {
  _.exclude("org.slf4j","slf4j-log4j12").exclude("com.google.inject", "guice").exclude("com.google.inject.extensions", "guice-assistedinject")
}

val jacksonVersion = "2.9.9" // "2.8.11"

libraryDependencies ++= Seq(
  "net.codingwell" %% "scala-guice" % "4.2.11",   // uses guice 4.2.3 (bellow)
  "com.google.inject" % "guice" % "4.2.3" classifier "no_aop",  // no_aop is set due to https://github.com/google/guice/issues/1133 // 4.0.1
  "com.google.inject.extensions" % "guice-assistedinject" % "4.2.3"
)

dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion
)

packagedArtifacts in publishLocal := {
  val artifacts: Map[sbt.Artifact, java.io.File] = (packagedArtifacts in publishLocal).value
  val assets: java.io.File = (playPackageAssets in Compile).value
  artifacts + (Artifact(moduleName.value, "jar", "jar", "assets") -> assets)
}

signedArtifacts := {
  val artifacts: Map[sbt.Artifact, java.io.File] = signedArtifacts.value
  val assets: java.io.File = (playPackageAssets in Compile).value
  artifacts ++ Seq(
    Artifact(moduleName.value, "jar", "jar",     "assets") -> assets,
    Artifact(moduleName.value, "jar", "jar.asc", "assets") -> new java.io.File(assets.getAbsolutePath + ".asc")  // manually sign assets.jar, uncomment, and republish
  )
}

// remove the custom conf from the produced jar
mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith("custom.conf")) }

// Asset stages

// TODO: need to find a solid replacement for uglify that support ES6
// check e.g. https://discuss.lightbend.com/t/is-there-any-sbt-plugin-which-will-uglify-es6/7488
pipelineStages in Assets := Seq(digest, gzip) // uglify

excludeFilter in gzip := (excludeFilter in gzip).value || new SimpleFileFilter(file => new File(file.getAbsolutePath + ".gz").exists)

//excludeFilter in rjs := (excludeFilter in rjs).value || GlobFilter("typeahead.js")

// excludeFilter in uglify := (excludeFilter in uglify).value || GlobFilter("typeahead.js")

// For licenses not automatically downloaded (need to list them manually)
licenseOverrides := {
  case
    DepModuleInfo("org.apache.commons", _, _)
    | DepModuleInfo("org.apache.curator", _, _)
    | DepModuleInfo("org.apache.directory.api", _, _)
    | DepModuleInfo("org.apache.directory.server", _, _)
    | DepModuleInfo("org.apache.httpcomponents", _, _)
    | DepModuleInfo("org.apache.hadoop", _, _)
    | DepModuleInfo("org.apache.parquet", _, _)
    | DepModuleInfo("org.apache.avro", _, _)
    | DepModuleInfo("commons-beanutils", "commons-beanutils", _)
    | DepModuleInfo("commons-beanutils", "commons-beanutils-core", _)
    | DepModuleInfo("commons-cli", "commons-cli", _)
    | DepModuleInfo("commons-codec", "commons-codec", _)
    | DepModuleInfo("commons-collections", "commons-collections", _)
    | DepModuleInfo("commons-io", "commons-io", _)
    | DepModuleInfo("commons-lang", "commons-lang", _)
    | DepModuleInfo("commons-logging", "commons-logging", _)
    | DepModuleInfo("commons-net", "commons-net", _)
    | DepModuleInfo("com.google.guava", "guava", _)
    | DepModuleInfo("com.google.inject", "guice", _)
    | DepModuleInfo("com.google.inject.extensions", "guice-multibindings", _)
    | DepModuleInfo("com.google.inject.extensions", "guice-assistedinject", "4.0")
    | DepModuleInfo("io.dropwizard.metrics", _, _)
    | DepModuleInfo("org.apache.xbean", "xbean-asm5-shaded", "4.4")
    | DepModuleInfo("org.apache.ivy", "ivy", "2.4.0")
    | DepModuleInfo("org.apache.zookeeper", "zookeeper", "3.4.6")
    | DepModuleInfo("com.fasterxml.jackson.module", "jackson-module-paranamer", "2.6.5")
    | DepModuleInfo("io.netty", "netty-all", "4.0.43.Final")
    | DepModuleInfo("com.bnd-lib", _, _)
    | DepModuleInfo("org.codehaus.jettison", "jettison", "1.1")
    | DepModuleInfo("org.htrace", "htrace-core", "3.0.4")
    | DepModuleInfo("org.mortbay.jetty", "jetty-util", "6.1.26")
    | DepModuleInfo("org.objenesis", "objenesis", "2.1")
    | DepModuleInfo("com.carrotsearch", "hppc", "0.7.1")
    | DepModuleInfo("com.github.lejon.T-SNE-Java", "tsne", "v2.5.0")
    | DepModuleInfo("oauth.signpost", "signpost-commonshttp4", "1.2.1.2")
    | DepModuleInfo("oauth.signpost", "signpost-core", "1.2.1.2")
    | DepModuleInfo("org.hibernate", "hibernate-validator", "5.2.4.Final")
    | DepModuleInfo("org.json4s", "json4s-ast_2.11", "3.2.11")
    | DepModuleInfo("org.json4s", "json4s-core_2.11", "3.2.11")
    | DepModuleInfo("org.json4s", "json4s-jackson_2.11", "3.2.11")
    | DepModuleInfo("javax.cache", "cache-api", "1.0.0")
    | DepModuleInfo("oro", "oro", "2.0.8")
    | DepModuleInfo("xerces", "xercesImpl", "2.9.1")
    | DepModuleInfo("net.java.dev.jna", "jna", _) // both jna and jna-platform libs have a dual LGPL / Apache 2.0 license, we choose Apache 2.0
    | DepModuleInfo("net.java.dev.jna", "jna-platform", _)
    | DepModuleInfo("cglib", "cglib-nodep", _)
    | DepModuleInfo("org.webjars", "bootswatch-united", "3.3.4+1")
  =>
    LicenseInfo(LicenseCategory.Apache, "Apache License v2.0", "http://www.apache.org/licenses/LICENSE-2.0")

  case
    DepModuleInfo("org.glassfish.hk2", "hk2-api", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.hk2", "hk2-locator", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.hk2", "hk2-utils", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.hk2", "osgi-resource-locator", "1.0.1")
    | DepModuleInfo("org.glassfish.hk2.external", "aopalliance-repackaged", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.hk2.external", "javax.inject", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.jersey.bundles.repackaged", "jersey-guava", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.containers", "jersey-container-servlet", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.containers", "jersey-container-servlet-core", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.core", "jersey-client", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.core", "jersey-common", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.core", "jersey-server", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.media", "jersey-media-jaxb", "2.22.2")
    | DepModuleInfo("javax.xml.bind", "jaxb-api", "2.2.2")
    | DepModuleInfo("javax.ws.rs", "javax.ws.rs-api", "2.0.1")
  =>
    LicenseInfo(LicenseCategory.GPLClasspath, "CDDL + GPLv2 with classpath exception", "https://javaee.github.io/glassfish/LICENSE")

  case
    DepModuleInfo("javax.mail", "mail", "1.4.7")
    | DepModuleInfo("com.sun.mail", "javax.mail", "1.5.6")
  =>
    LicenseInfo(LicenseCategory.GPLClasspath, "CDDL + GPLv2 with classpath exception", "https://javaee.github.io/javamail/LICENSE")

  case
    DepModuleInfo("javax.transaction", "jta", "1.1")
  =>
    LicenseInfo(LicenseCategory.GPLClasspath, "CDDL + GPLv2 with classpath exception", "https://github.com/javaee/javax.transaction/blob/master/LICENSE")

  case
    DepModuleInfo("com.esotericsoftware", "kryo-shaded", "3.0.3")
    | DepModuleInfo("org.hamcrest", "hamcrest-core", "1.3")
  =>
    LicenseInfo(LicenseCategory.BSD, "BSD 2-clause", "https://opensource.org/licenses/BSD-2-Clause")

  case
    DepModuleInfo("com.github.fommil.netlib", "core", "1.1.2")
    | DepModuleInfo("com.github.fommil", "jniloader", "1.1")
    | DepModuleInfo("org.antlr", "antlr4-runtime", "4.5.3")
    | DepModuleInfo("org.fusesource.leveldbjni", "leveldbjni-all", "1.8")
  =>
    LicenseInfo(LicenseCategory.BSD, "BSD 3-clause", "https://opensource.org/licenses/BSD-3-Clause")

  case
    DepModuleInfo("org.codehaus.janino", "commons-compiler", "3.0.0")
    | DepModuleInfo("org.codehaus.janino", "janino", "3.0.0")
  =>
    LicenseInfo(LicenseCategory.BSD, "New BSD License", "http://www.opensource.org/licenses/bsd-license.php")

  case DepModuleInfo("org.slf4j", _, _) =>
    LicenseInfo(LicenseCategory.MIT, "MIT", "http://opensource.org/licenses/MIT")

  case DepModuleInfo("org.bouncycastle", "bcprov-jdk15on", "1.51") =>
    LicenseInfo(LicenseCategory.MIT, "Bouncy Castle Licence", "http://www.bouncycastle.org/licence.html")

  case
    DepModuleInfo("com.h2database", "h2", "1.3.175") // h2database has a dual MPL / EPL license (http://h2database.com/html/license.html), we choose EPL
    | DepModuleInfo("junit", "junit", "4.12")
    | DepModuleInfo("ch.qos.logback", "logback-classic", _) // logback libs have a dual LGPL / EPL license, we choose EPL
    | DepModuleInfo("ch.qos.logback", "logback-core", _)
  =>
    LicenseInfo(LicenseCategory.EPL, "Eclipse Public License 1.0", "http://www.eclipse.org/legal/epl-v10.html")

  case
    DepModuleInfo("com.unboundid", "unboundid-ldapsdk", "2.3.8") // LDAP SDK has a ternary GPLv2 / GPLv2.1 / UnboundID LDAP SDK Free Use license, we choose the last one
  =>
    LicenseInfo(LicenseCategory.Unrecognized, "UnboundID LDAP SDK Free Use License", "https://github.com/pingidentity/ldapsdk/blob/master/LICENSE-UnboundID-LDAPSDK.txt")
}
