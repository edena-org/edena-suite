package org.edena.scripting

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}

class GraalPyAdminPoolForModuleInstallTest
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with GraalVmBaseContainer {

  private val graalPyPool = instance[GraalScriptPool, PyAdmin]

  override def afterAll(): Unit = {
    graalPyPool.close()
    super.afterAll()
  }

  "GraalPy Admin Pool with IO access" should "successfully import custom Python modules" in {
    val code = """
      |import sys
      |from pydub import AudioSegment
      |result = "pydub module imported successfully"
      |result
      |""".stripMargin

    graalPyPool.evalToString(code) match {
      case Right(result) =>
        result should include("pydub module imported successfully")
      case Left(error) =>
        fail(s"Custom Python module import should work: $error")
    }
  }
}