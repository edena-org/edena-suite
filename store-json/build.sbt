name := "edena-store-json"

description := "Store JSON stuff"

resolvers ++= Seq(
  Resolver.mavenLocal
)

val reactivemongoVersion = "1.1.0-RC12"
val reactivemongoPlayVersion = "1.1.0.play26-RC12"

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo-bson-api" % reactivemongoVersion  exclude("org.slf4j", "slf4j-api"),             // because of BSONObjectID
  "org.reactivemongo" %% "reactivemongo-play-json-compat" % reactivemongoPlayVersion  exclude("org.slf4j", "slf4j-api"),
)

dependencyOverrides ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.21"
)