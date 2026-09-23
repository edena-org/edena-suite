package org.edena.scripting

import org.edena.core.akka.guice.{AkkaModule, ConfigModule, GuiceContainer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsString, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Cross-caller isolation of pooled script contexts (the "S1-S7" leak scenarios).
 *
 * Every pool here has exactly ONE context, so consecutive evals are guaranteed to share it and
 * each scenario is deterministic: caller A leaves something behind, caller B looks for it.
 */
class GraalScriptPoolIsolationTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterAll
    with GuiceContainer {

  // No admin module: these tests only need the factories, and each GraalPy context is heavy.
  override protected def modules = Seq(
    new ConfigModule(),
    new AkkaModule(includeExecutionContext = true),
    new GraalVmModule()
  )

  private val pyFactory = instance[GraalPoolFactory, PyPoolFactory]
  private val jsFactory = instance[GraalPoolFactory, JsPoolFactory]

  private val py = pyFactory(GraalPoolConfig(poolSize = Some(1), description = Some("isolation test")))
  private val js = jsFactory(GraalPoolConfig(poolSize = Some(1), description = Some("isolation test")))
  private val pyFresh = pyFactory(
    GraalPoolConfig(poolSize = Some(1), freshContextPerUse = true, description = Some("isolation test - fresh"))
  )
  private val jsFresh = jsFactory(
    GraalPoolConfig(poolSize = Some(2), freshContextPerUse = true, description = Some("isolation test - fresh"))
  )

  private def impl(pool: GraalScriptPool) = pool.asInstanceOf[GraalScriptPoolImpl]

  private implicit val ec: ExecutionContext = ExecutionContext.global

  override def afterAll(): Unit = {
    Seq(py, js, pyFresh, jsFresh).foreach(_.close())
    super.afterAll()
  }

  private val recordA = """{"record":"A","secret":"s-A"}"""

  private def right[T](e: Either[String, T]): T =
    e.fold(err => fail(s"eval failed: $err"), identity)

  // ---------------------------------------------------------------------------------- Python

  private val pyListInjected =
    """json.dumps({k: str(globals()[k]) for k in list(globals()) if "_string_" in k})"""

  "Pooled Python (S1)" should "not expose a previous caller's injected JSON input" in {
    right(py.evalToJson("json.dumps(data)", Map("data" -> recordA))) shouldBe Json.parse(recordA)
    right(py.evalToJson(pyListInjected)) shouldBe Json.obj()
  }

  "Pooled Python (S2)" should "not accumulate injected inputs across callers" in {
    (1 to 3).foreach { i =>
      right(py.evalToJson("json.dumps(data)", Map("data" -> s"""{"record":"P$i"}""")))
    }
    right(py.evalToJson(pyListInjected)) shouldBe Json.obj()
  }

  it should "not expose the parsed input variable either" in {
    right(py.evalToJson("json.dumps(data)", Map("data" -> recordA)))
    right(py.evalToJson("json.dumps(globals().get('data', 'absent'))")) shouldBe JsString("absent")
  }

  "Pooled Python (S3)" should "not carry state stashed on builtins" in {
    right(py.evalToString("import builtins, json\nbuiltins.leak = 'from-A'\njson.dumps(1)"))
    right(py.evalToJson("import builtins, json\njson.dumps(getattr(builtins, 'leak', 'absent'))")) shouldBe
      JsString("absent")
  }

  "Pooled Python (S4)" should "leave no reset residue in globals" in {
    right(py.evalToString("x = 1\njson.dumps(x)"))
    right(py.evalToJson("json.dumps(sorted(k for k in list(globals()) if not k.startswith('__')))")) shouldBe
      Json.arr("datetime", "json", "sys")
  }

  "Pooled Python (S5)" should "not carry a plain user global (regression)" in {
    right(py.evalToString("probe = 'A'\njson.dumps(probe)"))
    right(py.evalToJson("json.dumps(globals().get('probe', 'absent'))")) shouldBe JsString("absent")
  }

  it should "restore a pre-imported module name the caller rebound" in {
    right(py.evalToString("json = 'hijacked'\n1"))
    right(py.evalToJson("json.dumps(type(json).__name__)")) shouldBe JsString("module")
  }

  "Pooled Python (S7)" should "not hand sabotaged builtins to the next caller" in {
    right(py.evalToString("import builtins\nbuiltins.globals = lambda: 1/0\n1"))
    right(py.evalToJson("json.dumps('__builtins__' in globals())")) shouldBe Json.toJson(true)

    right(py.evalToString("import builtins\ndel builtins.list\n1"))
    right(py.evalToJson("json.dumps(list((1, 2)))")) shouldBe Json.arr(1, 2)
  }

  // ------------------------------------------------------------------------------ JavaScript

  private val jsListInjected =
    "JSON.stringify(Object.getOwnPropertyNames(globalThis).filter(k => k.includes('_string_')))"

  "Pooled JavaScript (S6/S1)" should "not expose a previous caller's injected JSON input" in {
    right(js.evalToJson("JSON.stringify(data)", Map("data" -> recordA))) shouldBe Json.parse(recordA)
    right(js.evalToJson(jsListInjected)) shouldBe Json.arr()
    right(js.evalToString("typeof data")) shouldBe "undefined"
  }

  "Pooled JavaScript (S6/S3)" should "not carry prototype pollution" in {
    right(js.evalToString("Object.prototype.leak = 'from-A'; Array.prototype.leak2 = 'from-A'; 1"))
    right(js.evalToString("String(({}).leak) + ',' + String([].leak2)")) shouldBe "undefined,undefined"
  }

  it should "not carry a function stashed on globalThis" in {
    right(js.evalToString("globalThis.leakFn = function() { return 'from-A' }; 1"))
    right(js.evalToString("typeof leakFn")) shouldBe "undefined"
  }

  it should "not carry an overwritten built-in" in {
    right(js.evalToString("JSON.stringify = () => 'hijacked'; 1"))
    right(js.evalToString("JSON.stringify({a: 1})")) shouldBe """{"a":1}"""
  }

  "Pooled JavaScript (S6/S5)" should "not carry a plain user global (regression)" in {
    right(js.evalToString("probe = 'A'; probe"))
    right(js.evalToString("typeof probe")) shouldBe "undefined"
  }

  "Pooled JavaScript" should "not leak top-level declarations from a failing script" in {
    js.evalToString("const secretX = 's-A'; throw new Error('boom')").isLeft shouldBe true
    right(js.evalToString("typeof secretX")) shouldBe "undefined"
    // and the same declaration must work again for the next caller
    js.evalToString("const secretX = 's-B'; throw new Error('boom')") match {
      case Left(err) => err should include("boom")
      case Right(r)  => fail(s"expected an error, got $r")
    }
  }

  it should "run a failing script exactly once" in {
    // a re-execution on the error path would bump the counter and report run-2
    js.evalToString("globalThis.runs = (globalThis.runs || 0) + 1; throw new Error('run-' + globalThis.runs)") match {
      case Left(err) => err should (include("run-1") and not include ("run-2"))
      case Right(r)  => fail(s"expected an error, got $r")
    }
  }

  // -------------------------------------------------------------------------- failure paths (S7b)

  "A failed cleanup (S7b)" should "replace the context, never return it dirty" in {
    val pool = impl(py)
    val (failures, replacements, taintedReturns) =
      (pool.cleanupFailuresCount, pool.cleanupReplacementsCount, pool.taintedReturnsCount)

    pool.failCleanupForTesting = true
    try right(py.evalToString("probe = 's-A'\njson.dumps(data)", Map("data" -> recordA)))
    finally pool.failCleanupForTesting = false

    pool.cleanupFailuresCount shouldBe failures + 1
    pool.cleanupReplacementsCount shouldBe replacements + 1
    pool.taintedReturnsCount shouldBe taintedReturns
    right(py.evalToJson(pyListInjected)) shouldBe Json.obj()
    right(py.evalToJson("json.dumps(globals().get('probe', 'absent'))")) shouldBe JsString("absent")
  }

  it should "taint the context when no replacement can be created, and refuse to run on it" in {
    val pool = impl(py)
    val taintedReturns = pool.taintedReturnsCount

    pool.failCleanupForTesting = true
    pool.failReplacementForTesting = true
    try right(py.evalToString("probe = 's-A'\njson.dumps(data)", Map("data" -> recordA)))
    finally pool.failCleanupForTesting = false

    pool.taintedReturnsCount shouldBe taintedReturns + 1
    pool.taintedCount shouldBe 1

    // replacement still impossible -> the next caller fails fast instead of seeing A's state
    try an[IllegalStateException] should be thrownBy py.evalToString("json.dumps(globals().get('probe', 'absent'))")
    finally pool.failReplacementForTesting = false
    pool.taintedCount shouldBe 1 // the pool kept its size and the context stays quarantined

    // replacement possible again -> the tainted context is swapped before anyone runs on it
    right(py.evalToJson("json.dumps(globals().get('probe', 'absent'))")) shouldBe JsString("absent")
    right(py.evalToJson(pyListInjected)) shouldBe Json.obj()
    pool.taintedCount shouldBe 0
  }

  "A failed JS cleanup" should "replace the context, never return it dirty" in {
    val pool = impl(js)
    val replacements = pool.cleanupReplacementsCount

    pool.failCleanupForTesting = true
    try right(js.evalToString("globalThis.probe = 's-A'; JSON.stringify(data)", Map("data" -> recordA)))
    finally pool.failCleanupForTesting = false

    pool.cleanupReplacementsCount shouldBe replacements + 1
    right(js.evalToString("typeof probe")) shouldBe "undefined"
    right(js.evalToJson(jsListInjected)) shouldBe Json.arr()
  }

  // ---------------------------------------------------------------------------- isolated mode

  // state that pooled mode deliberately does not restore (documented): attributes of a module the
  // script imports itself, and top-level lexical declarations made through an indirect eval
  private val pyModuleStash = "import email\nemail.leak = 's-A'\njson.dumps(1)"
  private val pyModuleProbe = "import email\njson.dumps(getattr(email, 'leak', 'absent'))"
  private val jsLexicalStash = "(0, eval)(\"let stash = 's-A'\"); 1"
  private val jsLexicalProbe = "(0, eval)('typeof stash')"

  "An isolated Python eval" should "neither see nor leave state that pooled mode keeps" in {
    // isolated A leaves nothing in the pooled context
    right(py.evalToString(pyModuleStash, Map.empty, isolated = true))
    right(py.evalToJson(pyModuleProbe)) shouldBe JsString("absent")

    // pooled A stashes where the pooled reset cannot reach; an isolated B does not see it
    right(py.evalToString(pyModuleStash))
    right(py.evalToJson(pyModuleProbe, Map.empty, isolated = true)) shouldBe JsString("absent")
    impl(py).recreateAllContexts() // drop the stash so it cannot affect other tests
  }

  it should "get its bindings and handle errors like a pooled eval" in {
    right(py.evalToJson("json.dumps(data)", Map("data" -> recordA), isolated = true)) shouldBe Json.parse(recordA)
    right(py.evalToString("x + 1", Map("x" -> 41), isolated = true)) shouldBe "42"
    py.evalToString("1/0", Map.empty, isolated = true) match {
      case Left(err) => err should include("ZeroDivisionError")
      case Right(r)  => fail(s"expected an error, got $r")
    }
  }

  "A fresh-context-per-use Python pool" should "isolate every eval" in {
    right(pyFresh.evalToString(pyModuleStash))
    right(pyFresh.evalToJson(pyModuleProbe)) shouldBe JsString("absent")
    right(pyFresh.evalToString("import builtins\nbuiltins.leak = 'from-A'\njson.dumps(1)"))
    right(pyFresh.evalToJson("import builtins\njson.dumps(getattr(builtins, 'leak', 'absent'))")) shouldBe
      JsString("absent")
  }

  "An isolated JavaScript eval" should "neither see nor leave state that pooled mode keeps" in {
    right(js.evalToString(jsLexicalStash, Map.empty, isolated = true))
    right(js.evalToString(jsLexicalProbe)) shouldBe "undefined"

    right(js.evalToString(jsLexicalStash))
    right(js.evalToString(jsLexicalProbe, Map.empty, isolated = true)) shouldBe "undefined"
    impl(js).recreateAllContexts()
  }

  "A fresh-context-per-use JavaScript pool" should "isolate concurrent callers" in {
    val results = Await.result(
      Future.sequence((1 to 8).map { i =>
        Future(
          jsFresh.evalToString(
            "const seen = typeof mine; globalThis.mine = data.secret; Object.prototype.p = data.secret; seen + ':' + mine",
            Map("data" -> s"""{"secret":"s-$i"}""")
          )
        )
      }),
      2.minutes
    )
    results.map(right) shouldBe (1 to 8).map(i => s"undefined:s-$i")
  }

  // --------------------------------------------------------------------------------- timings

  "Per-eval cost" should "be reported for pooled vs isolated mode" in {
    def medianMs(n: Int)(eval: => Either[String, String]): Double = {
      (1 to 3).foreach(_ => right(eval)) // warm up
      val times = (1 to n).map { _ =>
        val start = System.nanoTime()
        right(eval)
        (System.nanoTime() - start) / 1e6
      }.sorted
      times(times.size / 2)
    }

    val pyInput = Map[String, Any]("data" -> recordA)
    val pyCode = "json.dumps(data['record'])"
    val jsCode = "JSON.stringify(data.record)"

    val pyPooled = medianMs(20)(py.evalToString(pyCode, pyInput))
    val pyIsolated = medianMs(10)(py.evalToString(pyCode, pyInput, isolated = true))
    val jsPooled = medianMs(20)(js.evalToString(jsCode, pyInput))
    val jsIsolated = medianMs(10)(js.evalToString(jsCode, pyInput, isolated = true))

    info(f"python: pooled $pyPooled%.1f ms, isolated $pyIsolated%.1f ms per eval (median)")
    info(f"js:     pooled $jsPooled%.1f ms, isolated $jsIsolated%.1f ms per eval (median)")
    println(f"[timing] python pooled=$pyPooled%.1f ms isolated=$pyIsolated%.1f ms; js pooled=$jsPooled%.1f ms isolated=$jsIsolated%.1f ms")
  }
}
