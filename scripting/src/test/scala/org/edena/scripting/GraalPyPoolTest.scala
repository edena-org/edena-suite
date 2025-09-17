package org.edena.scripting

import akka.stream.Materializer
import org.edena.core.util.parallelize
import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.{JsArray, JsObject}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class GraalPyPoolTest extends FlatSpec
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with GraalVmBaseContainer {

  private val graalPyPool = instance[GraalPyPool]
  private implicit val ec: ExecutionContext = instance[ExecutionContext]
  private implicit val materializer: Materializer = instance[Materializer]

  override def afterAll(): Unit = {
    graalPyPool.close()
    super.afterAll()
  }

  "GraalPyPool" should "evaluate simple Python expressions to string" in {
    val result = graalPyPool.evalToString("5 + 3")
    result should be(Right("8"))
  }

  it should "evaluate Python with variable bindings" in {
    val result = graalPyPool.evalToString("x + y", Map("x" -> 10, "y" -> 20))
    result should be(Right("30"))
  }

  it should "handle Python string operations" in {
    val result = graalPyPool.evalToString("'Hello ' + name", Map("name" -> "World"))
    result should be(Right("Hello World"))
  }

  it should "evaluate Python that returns JSON and parse it correctly" in {
    val input = 42
    val jsonResult = graalPyPool.evalToJson(
      """
        |import json
        |data = {"answer": x + 1, "input": x}
        |json.dumps(data)
        |""".stripMargin,
      bindings = Map("x" -> input)
    )

    jsonResult.isRight should be(true)
    val json = jsonResult.right.get.asInstanceOf[JsObject]
    (json \ "answer").as[Int] should be(43)
    (json \ "input").as[Int] should be(42)
  }

  it should "evaluate Python that returns JSON and parse it correctly (without an explicit json import)" in {
    val input = 42
    val jsonResult = graalPyPool.evalToJson(
      """
        |data = {"answer": x + 1, "input": x}
        |json.dumps(data)
        |""".stripMargin,
      bindings = Map("x" -> input)
    )

    jsonResult.isRight should be(true)
    val json = jsonResult.right.get.asInstanceOf[JsObject]
    (json \ "answer").as[Int] should be(43)
    (json \ "input").as[Int] should be(42)
  }

  it should "handle Python errors gracefully" in {
    val result = graalPyPool.evalToString("undefined_variable + 5")
    result.isLeft should be(true)
    result.left.get should include("Script execution error")
  }

  it should "handle invalid JSON in evalToJson" in {
    val result = graalPyPool.evalToJson("'invalid json'")
    result.isLeft should be(true)
    result.left.get should include("JSON parsing error")
  }

  it should "support Python list comprehensions" in {
    val code = """
      |numbers = [1, 2, 3, 4, 5]
      |squares = [x * x for x in numbers]
      |str(sum(squares))
      |""".stripMargin

    val result = graalPyPool.evalToString(code)
    result should be(Right("55")) // 1 + 4 + 9 + 16 + 25
  }

  it should "support Python dictionaries and functions" in {
    val code = """
      |def factorial(n):
      |    if n <= 1:
      |        return 1
      |    return n * factorial(n - 1)
      |
      |factorial(n)
      |""".stripMargin

    val result = graalPyPool.evalToString(code, Map("n" -> 5))
    result should be(Right("120")) // 5!
  }

  it should "handle concurrent execution efficiently" in {
    val repetitions = 100
    val parallelism = 10

    val startTime = System.currentTimeMillis()

    val futures = parallelize(1 to repetitions, Some(parallelism)) { i =>
      Future {
        val input = i % 1000
        val result = graalPyPool.evalToJson(
          """
            |import json
            |data = {"result": x * 3, "iteration": iter}
            |json.dumps(data)
            |""".stripMargin,
          bindings = Map("x" -> input, "iter" -> i)
        )
        (input, i, result)
      }
    }

    val results = Await.result(futures, 30.seconds)

    results.foreach { case (input, i, jsonResult) =>
      jsonResult.isRight should be(true)
      val json = jsonResult.right.get.asInstanceOf[JsObject]
      (json \ "result").as[Int] should be(input * 3)
      (json \ "iteration").as[Int] should be(i)
    }

    val duration = System.currentTimeMillis() - startTime
    println(s"Completed $repetitions concurrent JavaScript evaluations in ${duration}ms")
  }

  it should "handle Python data structures and return valid JSON" in {
    val result = graalPyPool.evalToJson(
      """
        |import json
        |data = {
        |    "numbers": [1, 2, 3, 4, 5],
        |    "doubled": [x * 2 for x in [1, 2, 3, 4, 5]],
        |    "nested": {"a": 1, "b": [1, 2, 3]}
        |}
        |json.dumps(data)
        |""".stripMargin
    )

    result.isRight should be(true)
    val json = result.right.get.asInstanceOf[JsObject]
    val doubled = (json \ "doubled").as[JsArray]
    doubled.value.map(_.as[Int]).sum should be(30) // sum of [2, 4, 6, 8, 10]
  }

  it should "support Python mathematical operations" in {
    val result = graalPyPool.evalToString(
      """
        |import math
        |result = math.sqrt(x) + math.pi
        |str(round(result, 2))
        |""".stripMargin,
      Map("x" -> 16)
    )

    result.isRight should be(true)
    // sqrt(16) + pi ≈ 4 + 3.14159 ≈ 7.14
    result.right.get.toDouble should be > 7.0
    result.right.get.toDouble should be < 8.0
  }
}