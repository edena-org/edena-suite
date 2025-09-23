import Dependencies.Versions

name := "edena-scripting"

description := "GraalVM scripting support for Python, JavaScript, and other languages"

resolvers ++= Seq(
  Resolver.mavenLocal
)

// GraalVM polyglot dependencies - requires JDK 17
libraryDependencies ++= Seq(
  "org.graalvm.polyglot" % "polyglot" % Versions.graalvm,
  "org.graalvm.polyglot" % "python-community" % Versions.graalvm pomOnly(),

  // needed for IsolateNativeModules but cannot use those because of:
  // Could not find VirtualFileSystem metadata in Java resources. Resource not found: org.graalvm.python.vfs/fileslist.txt
  //
  // "org.graalvm.python" % "python-embedding" % Versions.graalvm,
  // "org.graalvm.python" % "python-resources" % Versions.graalvm,

// "org.graalvm.polyglot" % "python" % Versions.graalvm pomOnly(), // Enterprise Python meta-POM
  "org.graalvm.polyglot" % "js-community" % Versions.graalvm pomOnly(),

  // Test
  "org.scalatest" %% "scalatest" % Versions.scalaTest % "test",
)