package org.edena.scripting

import org.graalvm.polyglot.Engine
import org.scalatest.FunSuite
import scala.jdk.CollectionConverters._

class GraalLangsTest extends FunSuite {
  test("list languages") {
    val engine = Engine.create()
    val languages = engine.getLanguages.keySet().asScala.mkString(", ")
    println("Installed languages in Test: " + languages)

    // Verify that at least JavaScript is available
    assert(engine.getLanguages.containsKey("js"), "JavaScript should be installed")

    // Check if Python is available
    if (engine.getLanguages.containsKey("python")) {
      println("Python is available!")
    } else {
      println("WARNING: Python is NOT available. Only these languages are installed: " + languages)
    }
  }
}
