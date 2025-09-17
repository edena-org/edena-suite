package org.edena.scripting

import akka.stream.Materializer
import org.edena.core.util.parallelize
import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.JsObject

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class GraalJSPoolTest extends FlatSpec
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with GraalVmBaseContainer {

  private val graalJsPool = instance[GraalJsPool]
  private implicit val ec: ExecutionContext = instance[ExecutionContext]
  private implicit val materializer: Materializer = instance[Materializer]

  override def afterAll(): Unit = {
    graalJsPool.close()
    super.afterAll()
  }

  "GraalJsPool" should "evaluate simple JavaScript expressions to string" in {
    val result = graalJsPool.evalToString("5 + 3")
    result should be(Right("8"))
  }

  it should "evaluate JavaScript with variable bindings" in {
    val result = graalJsPool.evalToString("x + y", Map("x" -> 10, "y" -> 20))
    result should be(Right("30"))
  }

  it should "return string representation of JavaScript objects" in {
    val result = graalJsPool.evalToString("""({name: "test", value: 42})""")
    result.isRight should be(true)
    result.right.get should include("test")
    result.right.get should include("42")
  }

  it should "evaluate JavaScript that returns JSON and parse it correctly" in {
    val input = 42
    val jsonResult = graalJsPool.evalToJson(
      """
        |var data = {"answer": x + 1, "input": x};
        |JSON.stringify(data);
        |""".stripMargin,
      bindings = Map("x" -> input)
    )

    println(jsonResult)
    jsonResult.isRight should be(true)
    val json = jsonResult.right.get.asInstanceOf[JsObject]
    (json \ "answer").as[Int] should be(43)
    (json \ "input").as[Int] should be(42)
  }

  it should "evaluate JavaScript to check consts have been cleaned up" in {
    val input = 42
    def callAux = graalJsPool.evalToJson(
      """
        |const data = {"answer": x + 1, "input": x};
        |JSON.stringify(data);
        |""".stripMargin,
      bindings = Map("x" -> input)
    )

    val jsonResult1 = callAux
    val jsonResult2 = callAux

    jsonResult1.isRight should be(true)
    val json1 = jsonResult1.right.get.asInstanceOf[JsObject]
    (json1 \ "answer").as[Int] should be(43)
    (json1 \ "input").as[Int] should be(42)

    jsonResult2.isRight should be(true)
    val json2 = jsonResult1.right.get.asInstanceOf[JsObject]
    (json2 \ "answer").as[Int] should be(43)
    (json2 \ "input").as[Int] should be(42)
  }

  it should "handle JavaScript errors gracefully" in {
    val result = graalJsPool.evalToString("undefined_variable + 5")
    result.isLeft should be(true)
    result.left.get should include("Script execution error")
  }

  it should "handle invalid JSON in evalToJson" in {
    val result = graalJsPool.evalToJson("'invalid json'")
    result.isLeft should be(true)
    result.left.get should include("JSON parsing error")
  }

  it should "support complex JavaScript operations" in {
    val code = """
      |function fibonacci(n) {
      |  if (n <= 1) return n;
      |  return fibonacci(n - 1) + fibonacci(n - 2);
      |}
      |fibonacci(n)
      |""".stripMargin

    val result = graalJsPool.evalToString(code, Map("n" -> 10))
    result should be(Right("55")) // 10th Fibonacci number
  }

  it should "handle concurrent execution efficiently" in {
    val repetitions = 100
    val parallelism = 10

    val startTime = System.currentTimeMillis()

    val futures = parallelize(1 to repetitions, Some(parallelism)) { i =>
      Future {
        val input = i % 1000
        val result = graalJsPool.evalToJson(
          """
            |var data = {"result": x * 2, "iteration": iter};
            |JSON.stringify(data);
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
      (json \ "result").as[Int] should be(input * 2)
      (json \ "iteration").as[Int] should be(i)
    }

    val duration = System.currentTimeMillis() - startTime
    println(s"Completed $repetitions concurrent JavaScript evaluations in ${duration}ms")
  }

  it should "handle concurrent execution efficiently with costs (to check cleanup)" in {
    val repetitions = 1000
    val parallelism = 100

    val startTime = System.currentTimeMillis()

    val futures = parallelize(1 to repetitions, Some(parallelism)) { i =>
      Future {
        val input = i % 1000
        val result = graalJsPool.evalToJson(
          """
            |const data = {"result": x * 2, "iteration": iter};
            |JSON.stringify(data);
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
      (json \ "result").as[Int] should be(input * 2)
      (json \ "iteration").as[Int] should be(i)
    }

    val duration = System.currentTimeMillis() - startTime
    println(s"Completed $repetitions concurrent JavaScript evaluations in ${duration}ms")
  }

  it should "handle array operations and return valid JSON" in {
    val result = graalJsPool.evalToJson(
      """
        |var arr = [1, 2, 3, 4, 5];
        |var doubled = arr.map(x => x * 2);
        |JSON.stringify({original: arr, doubled: doubled, sum: doubled.reduce((a, b) => a + b, 0)});
        |""".stripMargin
    )

    result.isRight should be(true)
    val json = result.right.get.asInstanceOf[JsObject]
    (json \ "sum").as[Int] should be(30) // sum of [2, 4, 6, 8, 10]
  }
}