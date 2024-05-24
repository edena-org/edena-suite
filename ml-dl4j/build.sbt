import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}

name := "edena-dl4j"

description := "Convenient wrapper of Deeplearning4J library especially for temporal classification."

isSnapshot := false

scalaVersion := "2.12.15" // "2.11.12"


val dl4jVersion = "1.0.0-M1.1"

// Java CPP setting
javaCppVersion := "1.5.5"
javaCppPresetLibs ++= Seq("openblas" -> "0.3.13", "opencv" -> "4.5.1", "ffmpeg" -> "4.3.2", "leptonica" -> "1.80.0", "hdf5" -> "1.12.0", "mkl" -> "2021.1")
fork:= true

// -Dorg.bytedeco.javacpp.platform.extension=-avx2.
// javaCppPlatform := Seq("linux-x86")

val excludeJavaCppBytedecoBindings = Seq("leptonica", "hdf5", "ffmpeg", "opencv", "openblas", "mkl", "javacpp").map(name => ExclusionRule(organization = "org.bytedeco", name = name))

// Linux native classifiers
// "linux-x86_64", "linux-x86_64-compat", "linux-x86_64-onednn", "linux-x86_64-avx2", "linux-x86_64-avx512", "linux-x86_64-onednn-avx2", "linux-x86_64-onednn-avx512" = Value

libraryDependencies ++= Seq(
  "org.deeplearning4j" % "deeplearning4j-core" % dl4jVersion excludeAll(excludeJavaCppBytedecoBindings :_*),
  "org.deeplearning4j" % "deeplearning4j-nlp" % dl4jVersion excludeAll(excludeJavaCppBytedecoBindings :_*),
  "org.nd4j" % "nd4j-native" % dl4jVersion excludeAll(excludeJavaCppBytedecoBindings :_*),
  "org.nd4j" % "nd4j-native" % dl4jVersion excludeAll(excludeJavaCppBytedecoBindings :_*) classifier "linux-x86_64-avx2",

//  "org.bytedeco" % "hdf5" % "1.12.0-1.5.5" classifier "linux-x86",
//  "org.bytedeco" % "ffmpeg" % "4.3.2-1.5.5" classifier "linux-x86",
//  "org.bytedeco" % "leptonica" % "1.80.0-1.5.5" classifier "linux-x86_64",
//  "org.bytedeco" % "opencv" % "4.5.1-1.5.5" classifier "linux-x86_64",
//  "org.bytedeco" % "openblas" % "0.3.13-1.5.5" classifier "linux-x86",

  "org.slf4j" % "slf4j-api" % "1.7.21",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
)

// For licenses not automatically downloaded (need to list them manually)
licenseOverrides := {
  case
    DepModuleInfo("org.deeplearning4j", _, _)
  | DepModuleInfo("org.nd4j", _, _)
  | DepModuleInfo("org.bytedeco.javacpp-presets", _, _)
  | DepModuleInfo("org.datavec", "datavec-api", _)
  | DepModuleInfo("org.datavec", "datavec-data-image", _)
  | DepModuleInfo("org.apache.commons", "commons-compress", _)
  | DepModuleInfo("org.apache.commons", "commons-lang3", _)
  | DepModuleInfo("org.apache.commons", "commons-math3", _)
  | DepModuleInfo("commons-codec", "commons-codec", _)
  | DepModuleInfo("commons-io", "commons-io", _)
  | DepModuleInfo("commons-net", "commons-net", _)
  | DepModuleInfo("commons-lang", "commons-lang", _)
  | DepModuleInfo("com.google.guava", "guava", _)
  | DepModuleInfo("com.google.code.gson", "gson", _)
  | DepModuleInfo("org.objenesis", "objenesis", "2.6") =>
    LicenseInfo(LicenseCategory.Apache, "Apache License v2.0", "http://www.apache.org/licenses/LICENSE-2.0")

  // logback libs have a dual LGPL / EPL license, we choose EPL
  case
    DepModuleInfo("ch.qos.logback", "logback-classic", _)
  | DepModuleInfo("ch.qos.logback", "logback-core", _)
  | DepModuleInfo("com.github.oshi", "oshi-core", "3.4.2") // note that oshi-core uses an MIT license from the version 3.13.0
    =>
    LicenseInfo(LicenseCategory.EPL, "Eclipse Public License 1.0", "http://www.eclipse.org/legal/epl-v10.html")

  case
    DepModuleInfo("com.twelvemonkeys.common", _, _)
  | DepModuleInfo("com.twelvemonkeys.imageio", _, _)
  | DepModuleInfo("com.github.os72", "protobuf-java-util-shaded-351", _)
  | DepModuleInfo("com.github.os72", "protobuf-java-shaded-351", _) =>
    LicenseInfo(LicenseCategory.BSD, "BSD-3 Clause", "http://opensource.org/licenses/BSD-3-Clause")
}