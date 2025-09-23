package org.edena.scripting

import akka.stream.Materializer
import org.edena.core.util.parallelize
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import play.api.libs.json.{JsArray, JsObject, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class GraalPyPoolTest
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with GraalVmBaseContainer {

  private val graalPyPool = instance[GraalScriptPool, PyDefault]
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

  it should "handle JSON processing and data manipulation" in {
    val code = """
                 |# Parse sample data from JSON string
                 |sample_data = json.loads(sample_data_str)
                 |
                 |# Process data
                 |result = {
                 |    'original_count': len(sample_data),
                 |    'numbers_sum': sum(sample_data['numbers']),
                 |    'strings_joined': ' '.join(sample_data['strings']),
                 |    'nested_access': sample_data['nested']['key2']['inner'],
                 |    'json_serializable': True
                 |}
                 |
                 |json.dumps(result)
                 |""".stripMargin

    val sampleData = Json.obj(
      "numbers" -> Json.arr(1, 2, 3, 4, 5),
      "strings" -> Json.arr("hello", "world", "python"),
      "nested" -> Json.obj(
        "key1" -> "value1",
        "key2" -> Json.obj("inner" -> "data")
      )
    )

    graalPyPool.evalToJson(code, Map("sample_data_str" -> sampleData.toString())) match {
      case Right(json) =>
        val obj = json.as[JsObject]
        (obj \ "original_count").as[Int] should be(3)
        (obj \ "numbers_sum").as[Int] should be(15)
        (obj \ "strings_joined").as[String] should be("hello world python")
        (obj \ "nested_access").as[String] should be("data")
        (obj \ "json_serializable").as[Boolean] should be(true)
      case Left(error) =>
        fail(s"JSON processing should work in Python: $error")
    }
  }

  it should "demonstrate error handling and exception management" in {
    val code = """
                 |results = {}
                 |
                 |# Test 1: Division by zero
                 |try:
                 |    x = 1 / 0
                 |    results['division'] = 'no_error'
                 |except ZeroDivisionError:
                 |    results['division'] = 'caught_zero_division'
                 |except Exception as e:
                 |    results['division'] = f'other_error: {type(e).__name__}'
                 |
                 |# Test 2: Invalid JSON
                 |try:
                 |    json.loads('invalid json')
                 |    results['json_parse'] = 'no_error'
                 |except json.JSONDecodeError:
                 |    results['json_parse'] = 'caught_json_error'
                 |except Exception as e:
                 |    results['json_parse'] = f'other_error: {type(e).__name__}'
                 |
                 |# Test 3: KeyError
                 |try:
                 |    d = {'a': 1}
                 |    value = d['missing_key']
                 |    results['key_access'] = 'no_error'
                 |except KeyError:
                 |    results['key_access'] = 'caught_key_error'
                 |except Exception as e:
                 |    results['key_access'] = f'other_error: {type(e).__name__}'
                 |
                 |# Test 4: Normal operation
                 |results['normal_op'] = 'works'
                 |
                 |json.dumps(results)
                 |""".stripMargin

    graalPyPool.evalToJson(code) match {
      case Right(json) =>
        val obj = json.as[JsObject]
        (obj \ "division").as[String] should be("caught_zero_division")
        (obj \ "json_parse").as[String] should be("caught_json_error")
        (obj \ "key_access").as[String] should be("caught_key_error")
        (obj \ "normal_op").as[String] should be("works")
      case Left(error) =>
        fail(s"Error handling should work properly: $error")
    }
  }

  it should "demonstrate list comprehensions and functional programming" in {
    val code = """
                 |# Sample data
                 |numbers = list(range(1, 11))  # [1, 2, 3, ..., 10]
                 |
                 |result = {
                 |    'original': numbers,
                 |    'squares': [x*x for x in numbers],
                 |    'evens': [x for x in numbers if x % 2 == 0],
                 |    'mapped_doubled': list(map(lambda x: x * 2, numbers[:5])),
                 |    'filtered_gt5': list(filter(lambda x: x > 5, numbers)),
                 |    'sum_all': sum(numbers),
                 |    'max_value': max(numbers),
                 |    'list_length': len(numbers)
                 |}
                 |
                 |json.dumps(result)
                 |""".stripMargin

    graalPyPool.evalToJson(code) match {
      case Right(json) =>
        val obj = json.as[JsObject]
        (obj \ "sum_all").as[Int] should be(55) // sum of 1-10
        (obj \ "max_value").as[Int] should be(10)
        (obj \ "list_length").as[Int] should be(10)
        val squares = (obj \ "squares").as[Seq[Int]]
        squares should contain(1)
        squares should contain(100)
        val evens = (obj \ "evens").as[Seq[Int]]
        evens should be(Seq(2, 4, 6, 8, 10))
      case Left(error) =>
        fail(s"List comprehensions should work: $error")
    }
  }

  it should "fail to make HTTP requests using urllib (no IO access)" in {
    val code = """
                 |import urllib.request
                 |import urllib.error
                 |import sys, codecs, encodings
                 |
                 |try:
                 |    # Make HTTP request to a working JSON API
                 |    with urllib.request.urlopen('https://api.github.com/zen') as response:
                 |        if response.status == 200:
                 |            data = response.read().decode('utf-8')
                 |            result = {
                 |                'success': True,
                 |                'status': response.status,
                 |                'has_data': data is not None and len(data.strip()) > 0,
                 |                'content_type': response.headers.get('Content-Type', 'unknown'),
                 |                'response_text': data.strip()
                 |            }
                 |        else:
                 |            result = {
                 |                'success': False,
                 |                'status': response.status,
                 |                'error': f'HTTP {response.status}'
                 |            }
                 |except Exception as e:
                 |    result = {
                 |        'success': False,
                 |        'error': str(e),
                 |        'error_type': type(e).__name__
                 |    }
                 |
                 |json.dumps(result)
                 |""".stripMargin

    graalPyPool.evalToJson(code) match {
      case Right(json) =>
        val obj = json.as[JsObject]
        (obj \ "success").as[Boolean] should be(false)
        obj.keys should contain("error")
        // Should fail due to IO restrictions
      case Left(error) =>
        // Expected to fail due to script execution error or IO restrictions
        error should include("Script execution error")
    }
  }

  it should "fail to perform file system operations (no IO access)" in {
    val code = """
                 |import os
                 |import tempfile
                 |
                 |try:
                 |    # Create a temporary file
                 |    with tempfile.NamedTemporaryFile(mode='w+', delete=False, suffix='.txt') as temp_file:
                 |        temp_file_path = temp_file.name
                 |        test_content = 'Hello from GraalPy admin pool!'
                 |        temp_file.write(test_content)
                 |        temp_file.flush()
                 |
                 |    # Read the file back
                 |    with open(temp_file_path, 'r') as read_file:
                 |        read_content = read_file.read()
                 |
                 |    # Clean up - delete the temporary file
                 |    os.unlink(temp_file_path)
                 |
                 |    result = {
                 |        'success': True,
                 |        'written_content': test_content,
                 |        'read_content': read_content,
                 |        'content_match': test_content == read_content,
                 |        'temp_file_path': temp_file_path,
                 |        'file_operations': 'complete'
                 |    }
                 |
                 |except Exception as e:
                 |    result = {
                 |        'success': False,
                 |        'error': str(e),
                 |        'error_type': type(e).__name__
                 |    }
                 |
                 |json.dumps(result)
                 |""".stripMargin

    graalPyPool.evalToJson(code) match {
      case Right(json) =>
        val obj = json.as[JsObject]
        (obj \ "success").as[Boolean] should be(false)
        obj.keys should contain("error")
        // Should fail due to IO restrictions
      case Left(error) =>
        // Expected to fail due to script execution error or IO restrictions
        error should include("Script execution error")
    }
  }
}
