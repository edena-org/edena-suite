package org.edena.scripting

import akka.actor.CoordinatedShutdown
import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits.ConfigExt
import org.graalvm.polyglot._
import org.graalvm.polyglot.proxy.ProxyExecutable

import java.util.concurrent.CompletableFuture
import javax.inject._
import scala.concurrent.ExecutionContext

final private class GraalJsPool @Inject() (
  config: Config,
  coordinatedShutdown: CoordinatedShutdown
)(
  implicit ec: ExecutionContext
) extends GraalScriptPoolImpl(
      language = "js",
      poolSize = config.optionalInt("graalvm.js.pool_size"),
      resetAfterEachUse =
        config.optionalBoolean("graalvm.js.reset_after_each_use").getOrElse(true),
      coordinatedShutdown
    ) {

  // Common JavaScript globals to preserve during reset
  private val preservedGlobals: Seq[String] = Seq(
    "console",
    "JSON",
    "Object",
    "Array",
    "String",
    "Number",
    "Boolean",
    "Date",
    "Math",
    "parseInt",
    "parseFloat",
    "isNaN",
    "isFinite",
    "setTimeout",
    "setInterval",
    "clearTimeout",
    "clearInterval"
  )
  // --------------------------------------------------------------------------

  // Implement abstract methods from GraalScriptPool
  protected def resetContext(ctx: Context): Unit = {
    // JavaScript context reset - clear user-defined variables but keep built-in globals
    val preservedList = preservedGlobals.map(g => s"'$g'").mkString(", ")
    val reset =
      s"""
         |var globalKeys = Object.getOwnPropertyNames(this);
         |var preserved = new Set(['undefined', 'NaN', 'Infinity', $preservedList]);
         |globalKeys.forEach(function(key) {
         |    if (!preserved.has(key) && typeof this[key] !== 'function') {
         |        delete this[key];
         |    }
         |});
         |""".stripMargin
    ctx.eval(language, reset)
  }

  protected def warmupContext(ctx: Context): Unit = {
    // JavaScript doesn't need explicit imports, but we can pre-compile common patterns
    // This helps with JIT compilation
    val warmupCode =
      """
        |// Warmup common JavaScript operations
        |var _warmup = {
        |    json: JSON.stringify({test: "warmup"}),
        |    math: Math.random(),
        |    date: new Date().toISOString()
        |};
        |delete _warmup;
        |""".stripMargin
    ctx.eval(language, warmupCode)
  }

  override protected def evalCore(
    code: String,
    bindings: Map[String, Any] = Map.empty,
    ctx: Context
  ): String = {
    val languageBindings = ctx.getBindings(language)
    bindings.foreach { case (k, v) => languageBindings.putMember(k, v) }
    //    val v = ctx.eval(language, code)
    val v = evalJsIsolatedAwait(ctx, code)
    if (v.isString) v.asString() else v.toString
  }

  private def evalJsIsolatedAwait(ctx: Context, code: String): Value = {
    // bind the code so JS can eval it
    val bindings = ctx.getBindings("js")
    bindings.putMember("__code", code)

    def await(p: Value): Value = {
      val fut = new CompletableFuture[Value]()
      val onFulfilled = new ProxyExecutable {
        override def execute(args: Value*): AnyRef = {
          fut.complete(args.headOption.getOrElse(Value.asValue(null))); null
        }
      }
      val onRejected = new ProxyExecutable {
        override def execute(args: Value*): AnyRef = {
          // Preserve PolyglotException if possible, otherwise create a RuntimeException
          val error = args.headOption.map(_.toString).getOrElse("Promise rejected")
          fut.completeExceptionally(new RuntimeException(error)); null
        }
      }
      p.invokeMember("then", onFulfilled)
      p.invokeMember("catch", onRejected)
      try {
        fut.get()
      } catch {
        case ex: java.util.concurrent.ExecutionException =>
          // Check if the cause was from JavaScript evaluation
          ex.getCause match {
            case _: RuntimeException =>
              // Try to execute the code directly to get the original PolyglotException
              ctx.eval(language, code)
            case other => throw other
          }
      }
    }

    try {
      // async IIFE so top-level `await` works inside the snippet
      // eval(__code) returns the completion value (last expression)
      val promise = ctx.eval("js", "(async () => { return eval(__code); })()")
      await(promise)
    } catch {
      case _: Exception =>
        // Fallback: execute directly to get proper PolyglotException
        ctx.eval(language, code)
    }
  }

}
