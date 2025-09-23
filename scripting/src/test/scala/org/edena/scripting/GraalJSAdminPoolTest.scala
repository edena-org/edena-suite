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
      |// Try to write a file using Node.js fs module
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
        // Expected to fail without Node.js modules
        error should include("Script execution error")
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
}
