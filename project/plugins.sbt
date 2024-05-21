addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.2.0")

// needed for dl4j
addSbtPlugin("org.bytedeco" % "sbt-javacpp" % "1.17")

// mongo migration
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.25")

// play
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.20")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")
// addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")
