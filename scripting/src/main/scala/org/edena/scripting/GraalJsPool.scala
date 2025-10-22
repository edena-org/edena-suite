package org.edena.scripting

import akka.actor.CoordinatedShutdown
import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits.ConfigExt
import org.graalvm.polyglot._
import org.graalvm.polyglot.proxy.ProxyExecutable
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala

final private class GraalJsPoolFactoryImpl @Inject() (
  appConfig: Config,
  coordinatedShutdown: CoordinatedShutdown
)(
  implicit ec: ExecutionContext
) extends GraalPoolFactory {

  override def apply(
    config: GraalPoolConfig,
    extendEngine: Option[Engine#Builder => Unit],
    extendContextBuilder: Option[(Context#Builder, Int) => Unit],
    extendContext: Option[Context => Unit]
  ): GraalScriptPool = {
    val configFinal = config.copy(
      poolSize = config.poolSize.orElse(
        appConfig.optionalInt("graalvm.js.pool_size")
      ),
      resetAfterEachUse = config.resetAfterEachUse.orElse(
        appConfig.optionalBoolean("graalvm.js.reset_after_each_use")
      )
    )

    new GraalJsPool(
      configFinal,
      extendEngine,
      extendContextBuilder,
      extendContext,
      coordinatedShutdown
    )
  }
}

final private class GraalJsPool(
  config: GraalPoolConfig,
  extendEngine: Option[Engine#Builder => Unit],
  extendContextBuilder: Option[(Context#Builder, Int) => Unit],
  extendContext: Option[Context => Unit],
  coordinatedShutdown: CoordinatedShutdown
)(
  implicit ec: ExecutionContext
) extends GraalScriptPoolImpl(
      language = "js",
      config,
      extendEngine,
      extendContextBuilder,
      extendContext,
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
    "clearInterval",
    "Polyglot", // so Polyglot.import keeps working
    "fsBridge", // if you expose it globally
    "httpBridge"
  )
  // --------------------------------------------------------------------------

  // Implement abstract methods from GraalScriptPool
  protected def resetContext(ctx: Context): Unit = {
    // Clean up old bindings
    val bindings = ctx.getBindings(language)
    val existingKeys = bindings.getMemberKeys.asScala.toSet
    // Known JavaScript variables that may appear during reset - these can be safely ignored
    val knownJsVariables = Set("globalKeys", "preserved")

    existingKeys.foreach { key =>
      try {
        bindings.removeMember(key)
      } catch {
        case _: UnsupportedOperationException =>
          // Ignore non-removable or non-existent members (e.g., JavaScript global variables)
          // Only log if it's not a known JavaScript variable to reduce noise
          if (!knownJsVariables.contains(key)) {
            logger.warn(s"Could not remove binding '$key' (this is usually harmless)")
          }
      }
    }

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
    // Using an IIFE (Immediately Invoked Function Expression) to avoid polluting global scope
    val warmupCode =
      """
        |// Warmup common JavaScript operations
        |(function() {
        |    var _warmup = {
        |        json: JSON.stringify({test: "warmup"}),
        |        math: Math.random(),
        |        date: new Date().toISOString()
        |    };
        |})();
        |""".stripMargin
    ctx.eval(language, warmupCode)
  }

  override protected def evalCore(
    code: String,
    bindings: Map[String, Any] = Map.empty,
    ctx: Context
  ): String = {
    val finalCode = substituteBindings(code, bindings, ctx)
    val v = evalJsIsolatedAwait(ctx, finalCode)
    if (v.isString) v.asString() else v.toString
  }

  override protected def parseJSONScript(
    varName: String,
    tempVarName: String
  ): String =
    s"const $varName = JSON.parse($tempVarName)"

  private def evalJsIsolatedAwait(
    ctx: Context,
    code: String
  ): Value = {
    // bind the code so JS can eval it
    val bindings = ctx.getBindings(language)
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
