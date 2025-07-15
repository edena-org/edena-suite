// The Typesafe repository
resolvers ++= Seq(
  "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/",
  "Maven Repo" at  "https://repo1.maven.org/maven2/"
)

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.2") // 3.9.15
//addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")

//addDependencyTreePlugin

//addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
//addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.2.0")

// addSbtPlugin("com.github.sbt" % "sbt-license-report" % "1.6.1")


// needed for dl4j
addSbtPlugin("org.bytedeco" % "sbt-javacpp" % "1.17")

// play
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.22") // 2.7.9
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")

//addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.0.0")
//addSbtPlugin("com.github.sbt" % "sbt-gzip" % "2.0.0")
// addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")

//addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.24") // 2.6.20
//addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "2.0.0")
//addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")

