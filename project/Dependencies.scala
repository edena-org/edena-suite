import sbt.librarymanagement.ModuleID
import sbt.librarymanagement.DependencyBuilders.Organization

object Dependencies {

  object Versions {
    val akka = "2.6.21"
    val scalaTest = "3.2.19" // 3.1+ moved style traits to org.scalatest.flatspec/funsuite/matchers packages
    val playJson = "2.10.6" // Updated for Play 2.9.x compatibility

    // Guice 5.1.0 was specifically released to provide proper Java 17 support
    // Guice 6.0+ supports Java 21
    val scalaGuice = "5.1.0" // compatible with Guice 5.1.0
    val googleGuice = "5.1.0" // compatible with play-mailer 9.0.1
    val guiceAssistedinject = "5.1.0"

    // JSON
    val jackson = "2.14.3"

    // Netty (transitive via Akka HTTP, Spark, Ignite, etc.) — pinned to fix CVE-2025-58056/58057/55163
    val netty = "4.1.127.Final"

    // Logback — pinned to fix CVE-2023-6378/6481 (receiver DoS) and CVE-2024-12798/12801 (fixed in 1.5.13+).
    // Effective runtime was 1.4.14 (ada-server, ml-dl4j) / 1.5.12 (ada-web via play-logback); slf4j 2.0.x already resolves.
    val logback = "1.5.34"

    // commons-lang3 — pinned to fix CVE-2025-48924 (ClassUtils recursion → StackOverflowError DoS, fixed in 3.18.0).
    // elastic resolved 3.12.0 (vulnerable); ada-server/ada-web already pulled 3.20.0 transitively. Only StringEscapeUtils
    // is used in our code (deprecated since 3.6 but still present in 3.x). 3.20.0 is the highest already in play — no downgrade.
    val commonsLang3 = "3.20.0"

    // commons-io — pinned to fix CVE-2024-47554 (uncontrolled resource consumption, fixed in 2.14.0). core/build.sbt
    // declared a misleading 2.6 that actually resolved to 2.6 in core/ml-spark (vulnerable) and 2.16.1 elsewhere.
    // Only IOUtils.toInputStream(CharSequence, String) is used (ml-dl4j) — stable across all 2.x, no API break.
    val commonsIo = "2.18.0"

    // ES
    val elastic4s = "8.19.1" // Latest 8.x - compatible with Akka 2.6.x, Play JSON 2.10.x, Jackson overridden to 2.14.x

    // MONGO
    // reactivemongo-akkastream uses akka-stream 2.5.23
    val reactivemongo = "1.1.0-RC12"
    // uses play json 2.7.4 but we override to 2.8.2
//    val reactivemongoPlay = "1.1.0.play28-RC12"

    val reactivemongoPlay = "1.1.0.play29-RC12"

    // WS
    // JSON WS Streaming
    val akkaHttp = "10.2.10" // compatible with Akka 2.6.21

    // SPARK
    val spark = "3.5.6" // patch bump with CVE fixes; 4.0 deferred (breaking changes)
    val bnd = "0.7.3"

    // IGNITE
    val ignite = "2.17.0" // CVE-2024-52577 (RCE) fixed; brings Spring 5.3.x, H2 past its CVEs

    // WS + ADA-SERVER
    val playWs = "2.2.10" // compatible with Akka 2.6.21 and Play 2.9.6 uses it
//    val playWs = "2.1.11" // compatible with Akka 2.6.21
    val breeze = "2.1.0"

    // PLAY
    val play = "2.9.6"
    val deadbolt = "2.9.0"
    val webjars = "2.9.1"

    // ADA-WEB
//    val playMongo = "1.1.0.play28-RC12"
    val playMongo = "1.1.0.play29-RC12"

//    val playMailer = "8.0.1"
    val playMailer = "9.0.1" // play 2.9.5, play-mailer-guice -> guice 6.0.0
//    val playPac4j = "11.1.0-PLAY2.8" //"10.0.2"
    val playPac4j = "12.0.1-PLAY2.9" //"10.0.2"
    val pac4jOidc = "6.3.3" // 6.3.3 has CVE-2026-29000 fix for pac4j-jwt (not directly on classpath, hygiene bump)

    val scalazCore = "7.2.36"
    val scalatestplusPlay = "6.0.2" // Play 2.9.x + scalatest 3.2.x

    // GraalVM
    val graalvm = "24.2.0"   // JDK 17
  }
}
