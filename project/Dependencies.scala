object Dependencies {

  object Versions {
    val akka = "2.5.32"
    val scalaTest = "3.0.8" // originally "3.0.0"
    val playJson = "2.7.4"

    // val playVersion = "2.6.20" // JDK 17 is supported by Play 2.8 https://github.com/playframework/playframework/releases/tag/2.8.15
    // val guiceVersion = "5.1.0" // needed for JDK 17

    // ES
    val elastic4s = "7.2.0"

    // MONGO
    // reactivemongo-akkastream uses akka-stream 2.5.23
    val reactivemongo = "1.1.0-RC12"
    // uses play json 2.7.4
    val reactivemongoPlay = "1.1.0.play27-RC12"

    // WS
    // JSON WS Streaming
    val akkaHttp = "10.1.10" // originally "10.0.14"

    // SPARK
    val spark = "2.4.7" // Spark 3 is JDK 11 compatible, should upgrade (the latest version is 3.2.1)
    val bnd = "0.7.3" // "0.7.3.RC.1"

    // IGNITE
    val ignite = "2.4.0" //  "2.14.0" - uses JDK 17

    // WS + ADA-SERVER
    // uses Akka 2.5.23
    val playWs = "2.0.8" // originally "1.1.10" for WS
    val breeze = "2.1.0" // originally "0.13.2"

    // ADA-WEB
    val playMongo = "1.1.0.play27-RC12"
    val play = "2.7.9"
    val playMailer = "6.0.1"
    val playPac4j = "9.0.2"
    val pac4jOidc = "4.1.0"
    val jackson = "2.9.9"
  }
}
