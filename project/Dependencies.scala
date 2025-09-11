import sbt.librarymanagement.ModuleID
import sbt.librarymanagement.DependencyBuilders.Organization

object Dependencies {

  object Versions {
    val akka = "2.6.21"
    val scalaTest = "3.0.8" // originally "3.0.0"
//    val playJson = "2.8.2" // Updated for Play 2.8.x compatibility
    val playJson = "2.10.6" // Updated for Play 2.9.x compatibility

    // Guice 5.1.0 was specifically released to provide proper Java 17 support
    // Guice 6.0+ supports Java 21
    val scalaGuice = "5.1.0" // compatible with Guice 5.1.0
    val googleGuice = "5.1.0" // compatible with play-mailer 9.0.1
    val guiceAssistedinject = "5.1.0"

    // JSON
//    val jackson = "2.13.3"
    val jackson = "2.14.3"

    // ES
    val elastic4s = "7.10.8" // Available version close to 7.10.6

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
    val spark = "3.5.4"
    val bnd = "0.7.3"

    // IGNITE
    val ignite = "2.14.0" //  "2.14.0" - uses JDK 17

    // WS + ADA-SERVER
    val playWs = "2.1.11" // compatible with Akka 2.6.21
    val breeze = "2.1.0"

    // PLAY
//    val play = "2.8.22"  // "com.typesafe.play" %% "play" % "2.8.22" - akka 2.6.21
//    val deadbolt = "2.8.2"  // updated for Play 2.8.x and Scala 2.13
//    val webjars = "2.8.0"

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
    val pac4jOidc = "6.2.1" // 5.3.1

    val scalazCore = "7.2.36"
    val scalatestplusPlay = "4.0.3"
  }
}
