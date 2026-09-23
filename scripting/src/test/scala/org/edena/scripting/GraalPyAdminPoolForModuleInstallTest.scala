package org.edena.scripting

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GraalPyAdminPoolForModuleInstallTest
    extends AnyFlatSpec
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

  it should "import venv packages in an isolated (fresh-context) eval too" in {
    val code = """
      |import sys
      |from pydub import AudioSegment
      |sys.prefix
      |""".stripMargin

    graalPyPool.evalToString(code, Map.empty, isolated = true) match {
      case Right(prefix) =>
        prefix should startWith("/opt/graalpy-venvs/ctx-") // the slot's venv, like a pooled context
      case Left(error) =>
        fail(s"Custom Python module import should work in an isolated eval: $error")
    }
  }
}
