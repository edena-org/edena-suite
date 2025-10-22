package org.edena.scripting

import akka.stream.Materializer
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext

class GraalPyAdminPoolTest
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with GraalVmBaseContainer {

  private val graalPyPool = instance[GraalScriptPool, PyAdmin]
  private implicit val ec: ExecutionContext = instance[ExecutionContext]
  private implicit val materializer: Materializer = instance[Materializer]

  override def afterAll(): Unit = {
    graalPyPool.close()
    super.afterAll()
  }

  "GraalPy Admin Pool with IO access" should "have basic Python functionality available" in {
    val code = """
      |import json
      |import sys
      |import datetime
      |
      |result = {
      |    'python_version': sys.version,
      |    'datetime_available': 'datetime' in dir(),
      |    'json_available': 'json' in dir(),
      |    'timestamp': datetime.datetime.now().isoformat()
      |}
      |
      |json.dumps(result)
      |""".stripMargin

    graalPyPool.evalToJson(code) match {
      case Right(json) =>
        println(json)
        val obj = json.as[JsObject]
        obj.keys should contain("python_version")
        obj.keys should contain("datetime_available")
        obj.keys should contain("json_available")
        obj.keys should contain("timestamp")
        (obj \ "datetime_available").as[Boolean] should be(true)
        (obj \ "json_available").as[Boolean] should be(true)
      case Left(error) =>
        fail(s"Basic Python functionality should work: $error")
    }
  }

  it should "successfully make HTTP requests using urllib" in {
    val code = """
      |import urllib.request
      |import urllib.error
      |import sys, codecs, encodings
      |import encodings.idna
      |
      |try:
      |    # Make HTTP request to a working JSON API
      |    with urllib.request.urlopen('https://postman-echo.com/get?foo=bar') as response:
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
        println(json)
        val obj = json.as[JsObject]
        (obj \ "success").as[Boolean] should be(true)
        (obj \ "status").as[Int] should be(200)
        (obj \ "has_data").as[Boolean] should be(true)
        val responseText = (obj \ "response_text").as[String]
        responseText.length should be > 0
        // GitHub Zen API returns inspirational text
      case Left(error) =>
        fail(s"HTTP request should succeed in Python admin pool: $error")
    }
  }

  it should "successfully perform file system operations" in {
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
        println(json)
        val obj = json.as[JsObject]
        (obj \ "success").as[Boolean] should be(true)
        (obj \ "content_match").as[Boolean] should be(true)
        (obj \ "written_content").as[String] should be("Hello from GraalPy admin pool!")
        (obj \ "read_content").as[String] should be("Hello from GraalPy admin pool!")
        (obj \ "file_operations").as[String] should be("complete")
      case Left(error) =>
        fail(s"File system operations should succeed in Python admin pool: $error")
    }
  }

  it should "handle JSON Strings as inputs" in {
    val json = Json.obj(
      "key1" -> "value1",
      "key2" -> 123,
      "flag" -> false,
      "key3" -> Json.obj(
        "nestedKey1" -> true,
        "nestedKey2" -> Json.arr(1, 2, 3)
      )
    )
    val result = graalPyPool.evalToString(
      """
        |x['key1'] + " and " + str(x['key3']['nestedKey2'][1])
        |""".stripMargin,
      Map("x" -> json.toString())
    )
    result.isRight should be(true)
    result.right.get should include("value1 and 2")
  }

  it should "handle JSONs as inputs" in {
    val json = Json.obj(
      "key1" -> "value1",
      "key2" -> 123,
      "flag" -> false,
      "key3" -> Json.obj(
        "nestedKey1" -> true,
        "nestedKey2" -> Json.arr(1, 2, 3)
      )
    )
    val result = graalPyPool.evalToString(
      """
        |x['key1'] + " and " + str(x['key3']['nestedKey2'][1])
        |""".stripMargin,
      Map("x" -> json)
    )
    result.isRight should be(true)
    result.right.get should include("value1 and 2")
  }
}