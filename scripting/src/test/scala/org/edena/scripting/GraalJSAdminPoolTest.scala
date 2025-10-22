package org.edena.scripting

import akka.stream.Materializer
import org.edena.core.util.parallelize
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class GraalJSAdminPoolTest
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with GraalVmBaseContainer {

  private val graalJsPool = instance[GraalScriptPool, JsAdmin]
  private implicit val ec: ExecutionContext = instance[ExecutionContext]
  private implicit val materializer: Materializer = instance[Materializer]

  override def afterAll(): Unit = {
    graalJsPool.close()
    super.afterAll()
  }

  "GraalJS Admin Pool with IO access" should "have Polyglot functionality available" in {
    val code = """
      |// Check if Polyglot is available and functional
      |JSON.stringify({
      |  polyglotExists: typeof Polyglot !== 'undefined',
      |  hasImport: typeof Polyglot !== 'undefined' && typeof Polyglot.import === 'function',
      |  hasExport: typeof Polyglot !== 'undefined' && typeof Polyglot.export === 'function'
      |});
      |""".stripMargin

    graalJsPool.evalToJson(code) match {
      case Right(json) =>
        println(json)
        val obj = json.as[JsObject]
        (obj \ "polyglotExists").as[Boolean] should be(true)
        (obj \ "hasImport").as[Boolean] should be(true)
        (obj \ "hasExport").as[Boolean] should be(true)
      case Left(error) =>
        fail(s"Polyglot sanity check failed: $error")
    }
  }

  it should "successfully make HTTP requests using fetch via shim bridge with require" in {
    val code = """
      |(async () => {
      |  try {
      |    // Import fetch via require (should work in admin pool)
      |    const fetch = require('fetch');
      |
      |    // Make HTTP request to allowed host
      |    const response = await fetch('https://httpbin.org/json');
      |    const data = await response.json();
      |
      |    return JSON.stringify({
      |      success: true,
      |      status: response.status,
      |      ok: response.ok,
      |      hasData: data && typeof data === 'object'
      |    });
      |  } catch (e) {
      |    return JSON.stringify({
      |      success: false,
      |      error: e.message,
      |      errorType: e.constructor.name
      |    });
      |  }
      |})()
      |""".stripMargin

    // This should now succeed since the admin pool has fetch capability
    graalJsPool.evalToJson(code) match {
      case Right(json) =>
        (json \ "success").as[Boolean] should be(true)
        (json \ "ok").as[Boolean] should be(true)
        (json \ "status").as[Int] should be(200)
        (json \ "hasData").as[Boolean] should be(true)
      case Left(error) =>
        fail(s"HTTP request should succeed in admin pool: $error")
    }
  }

  it should "attempt to write files using pure JavaScript" in {
    val code = """
      |try {
      |  const fs = require('fs');
      |  const content = 'Test content from GraalJS';
      |  fs.writeFileSync('/tmp/graal-test.txt', content);
      |
      |  // Try to read it back
      |  const readBack = fs.readFileSync('/tmp/graal-test.txt', 'utf8');
      |  JSON.stringify({
      |    success: true,
      |    written: content,
      |    read: readBack,
      |    match: content === readBack
      |  });
      |} catch (e) {
      |  JSON.stringify({
      |    success: false,
      |    error: e.message,
      |    errorType: e.constructor.name
      |  });
      |}
      |""".stripMargin

    graalJsPool.evalToJson(code) match {
      case Right(json) =>
        println(json)
        (json \ "success").as[Boolean] should be(true)
      case Left(error) =>
        fail(s"File write/read should work in admin pool: $error")
    }
  }

  it should "demonstrate basic JavaScript capabilities without IO" in {
    val code = """
      |// Basic JavaScript that should work
      |const data = {
      |  timestamp: new Date().toISOString(),
      |  random: Math.random(),
      |  calculated: [1,2,3,4,5].reduce((a, b) => a + b, 0),
      |  asyncTest: (async () => {
      |    return Promise.resolve('async works');
      |  })()
      |};
      |
      |// Wait for async operation
      |(async () => {
      |  const asyncResult = await data.asyncTest;
      |  return JSON.stringify({
      |    ...data,
      |    asyncResult: asyncResult,
      |    capabilities: 'basic_js_works'
      |  });
      |})()
      |""".stripMargin

    graalJsPool.evalToString(code) match {
      case Right(result) =>
        result should include("basic_js_works")
        result should include("async works")
        // Verify it's valid JSON
        val _ = play.api.libs.json.Json.parse(result)
      case Left(error) =>
        fail(s"Basic JavaScript should work: $error")
    }
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

  it should "handle concurrent execution efficiently with costs (to check cleanup) with fs" in {
    val repetitions = 1000
    val parallelism = 100

    val startTime = System.currentTimeMillis()

    val futures = parallelize(1 to repetitions, Some(parallelism)) { i =>
      Future {
        val input = i % 1000
        val result = graalJsPool.evalToJson(
          """
            |try {
            |  const fs = require('fs');
            |  const content = 'Test content from GraalJS';
            |  fs.writeFileSync('/tmp/graal-test.txt' + x, content);
            |
            |  // Try to read it back
            |  const readBack = fs.readFileSync('/tmp/graal-test.txt' + x, 'utf8');
            |  JSON.stringify({
            |    success: true,
            |    written: content,
            |    read: readBack,
            |    match: content === readBack
            |  });
            |} catch (e) {
            |  JSON.stringify({
            |    success: false,
            |    error: e.message,
            |    errorType: e.constructor.name
            |  });
            |}
            |""".stripMargin,
          bindings = Map("x" -> input)
        )
        (input, i, result)
      }
    }

    val results = Await.result(futures, 30.seconds)

    results.foreach { case (input, i, jsonResult) =>
      jsonResult.isRight should be(true)
      val json = jsonResult.right.get.asInstanceOf[JsObject]
      val success = (json \ "success").as[Boolean]
      if (!success) {
        println(s"File operation failed in iteration $i: " + json + s" with ${json \ "error"}")
      }
      success should be(true)
    }

    val duration = System.currentTimeMillis() - startTime
    println(s"Completed $repetitions concurrent JavaScript evaluations in ${duration}ms")
  }

  it should "handle concurrent execution efficiently with costs (to check cleanup) with fetch" in {
    val repetitions = 10
    val parallelism = 5

    val startTime = System.currentTimeMillis()

    val futures = parallelize(1 to repetitions, Some(parallelism)) { i =>
      Future {
        val result = graalJsPool.evalToJson(
          """
            |(async () => {
            |  try {
            |    // Import fetch via require (should work in admin pool)
            |    const fetch = require('fetch');
            |
            |    // Make HTTP request to allowed host
            |    const response = await fetch('https://httpbin.org/json');
            |    const data = await response.json();
            |
            |    return JSON.stringify({
            |      success: true,
            |      status: response.status,
            |      ok: response.ok,
            |      hasData: data && typeof data === 'object'
            |    });
            |  } catch (e) {
            |    return JSON.stringify({
            |      success: false,
            |      error: e.message,
            |      errorType: e.constructor.name
            |    });
            |  }
            |})()
            |""".stripMargin,
        )
        (i, result)
      }
    }

    val results = Await.result(futures, 2.minutes)

    results.foreach { case (i, jsonResult) =>
      jsonResult.isRight should be(true)
      val json = jsonResult.right.get.asInstanceOf[JsObject]
      val success = (json \ "success").as[Boolean]
      if (!success) {
        println(s"Request $i failed: " + json)
      }
      success should be(true)
    }

    val duration = System.currentTimeMillis() - startTime
    println(s"Completed $repetitions concurrent JavaScript evaluations in ${duration}ms")
  }

  it should "list files using fs.promises.readdir (async)" in {
    val code = """
                 |const fs = require('fs');
                 |
                 |(async function() {
                 |  try {
                 |    const files = await fs.promises.readdir(folderPath, { withFileTypes: true });
                 |
                 |    const fileInfo = files.slice(0, 5).map(file => ({
                 |      name: file.name(),
                 |      isFile: file.isFile(),
                 |      isDirectory: file.isDirectory()
                 |    }));
                 |
                 |    return JSON.stringify({
                 |      success: true,
                 |      folderPath: folderPath,
                 |      totalFiles: files.length,
                 |      sampleFiles: fileInfo,
                 |      hasFiles: files.length > 0
                 |    });
                 |  } catch (e) {
                 |    return JSON.stringify({
                 |      success: false,
                 |      error: e.message,
                 |      errorType: e.constructor.name
                 |    });
                 |  }
                 |})()
                 |""".stripMargin

    val folder = "/tmp"  // Use /tmp which should have files

    graalJsPool.evalToJson(code, Map("folderPath" -> folder)) match {
      case Right(json) =>
        val obj = json.as[JsObject]
        println("Async readdir result: " + json)
        (obj \ "success").as[Boolean] should be(true)
        (obj \ "folderPath").as[String] should be(folder)
        (obj \ "totalFiles").as[Int] should be >= 0
        (obj \ "hasFiles").as[Boolean] should be(true)
      case Left(error) =>
        fail(s"Async file listing should work: $error")
    }
  }

  it should "list files using fs.readdirSync (sync)" in {
    val code = """
                 |const fs = require('fs');
                 |
                 |try {
                 |  const files = fs.readdirSync(folderPath, { withFileTypes: true });
                 |
                 |  const fileInfo = files.slice(0, 5).map(file => ({
                 |    name: file.name(),
                 |    isFile: file.isFile(),
                 |    isDirectory: file.isDirectory()
                 |  }));
                 |
                 |  JSON.stringify({
                 |    success: true,
                 |    folderPath: folderPath,
                 |    totalFiles: files.length,
                 |    sampleFiles: fileInfo,
                 |    hasFiles: files.length > 0
                 |  });
                 |} catch (e) {
                 |  JSON.stringify({
                 |    success: false,
                 |    error: e.message,
                 |    errorType: e.constructor.name
                 |  });
                 |}
                 |""".stripMargin

    val folder = "/tmp"  // Use /tmp which should have files

    graalJsPool.evalToJson(
      code,
      Map("folderPath" -> folder)
    ) match {
      case Right(json) =>
        val obj = json.as[JsObject]
        println("Sync readdirSync result: " + json)
        (obj \ "success").as[Boolean] should be(true)
        (obj \ "folderPath").as[String] should be(folder)
        (obj \ "totalFiles").as[Int] should be >= 0
        (obj \ "hasFiles").as[Boolean] should be(true)
      case Left(error) =>
        fail(s"Sync file listing should work: $error")
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
    val result = graalJsPool.evalToString(
      """
        |x.key1 + " and " + x.key3.nestedKey2[1];
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
    val result = graalJsPool.evalToString(
      """
        |x.key1 + " and " + x.key3.nestedKey2[1];
        |""".stripMargin,
      Map("x" -> json)
    )
    result.isRight should be(true)
    result.right.get should include("value1 and 2")
  }


}
