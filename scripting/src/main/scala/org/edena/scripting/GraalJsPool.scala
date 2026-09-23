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
      ),
      freshContextPerUse = config.freshContextPerUse ||
        appConfig.optionalBoolean("graalvm.js.fresh_context_per_use").getOrElse(false)
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

  // Implement abstract methods from GraalScriptPool
  protected def resetContext(ctx: Context): Unit =
    runRestorer(ctx)

  import GraalJsPool.{CodeBindingName, JsRejection}

  override protected def internalBindingNames: Set[String] = Set(CodeBindingName)

  /**
   * Snapshot the own properties (full descriptors) of `globalThis` and of the core built-in
   * objects and prototypes right after warmup, and return a function restoring them: added
   * properties are deleted, replaced/deleted ones are put back. `Object.prototype` goes first so
   * prototype pollution cannot interfere with the descriptor handling of the others. Everything the
   * restorer uses is captured at creation time, so a script that overwrites `Reflect`, `Object` etc.
   * cannot break or subvert the reset. (The previous reset deleted every non-function global,
   * including `globalThis`, `Reflect` and `Intl` — they were gone after a context's first use.)
   */
  override protected def createRestorer(ctx: Context): Value =
    ctx.eval(
      language,
      """(function () {
        |  'use strict';
        |  const ownKeys = Reflect.ownKeys, getDesc = Object.getOwnPropertyDescriptor,
        |        defProp = Object.defineProperty, delProp = Reflect.deleteProperty, create = Object.create;
        |  const targets = [
        |    Object.prototype, Function.prototype, Array.prototype, String.prototype, Number.prototype,
        |    Boolean.prototype, Symbol.prototype, Date.prototype, RegExp.prototype, Error.prototype,
        |    Promise.prototype, Map.prototype, Set.prototype,
        |    Object, Function, Array, String, Number, Boolean, Symbol, Date, RegExp, Error, Promise, Map, Set,
        |    JSON, Math, Reflect, globalThis
        |  ];
        |  const snapshots = [];
        |  for (let i = 0; i < targets.length; i++) {
        |    const t = targets[i], keys = ownKeys(t), byKey = create(null);
        |    for (let j = 0; j < keys.length; j++) byKey[keys[j]] = getDesc(t, keys[j]);
        |    snapshots[i] = { keys: keys, byKey: byKey };
        |  }
        |  return function () {
        |    for (let i = 0; i < targets.length; i++) {
        |      const t = targets[i], snapshot = snapshots[i], current = ownKeys(t);
        |      for (let j = 0; j < current.length; j++) {
        |        const k = current[j];
        |        if (!(k in snapshot.byKey)) { try { delProp(t, k); } catch (e) {} }
        |      }
        |      for (let j = 0; j < snapshot.keys.length; j++) {
        |        const k = snapshot.keys[j], d = snapshot.byKey[k], c = getDesc(t, k);
        |        if (c === undefined || c.value !== d.value || c.get !== d.get || c.set !== d.set ||
        |            c.writable !== d.writable || c.enumerable !== d.enumerable || c.configurable !== d.configurable) {
        |          try { defProp(t, k, d); } catch (e) {}
        |        }
        |      }
        |    }
        |  };
        |})()
        |""".stripMargin
    )

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
    prepared: PreparedEval,
    ctx: Context
  ): String = {
    putBindings(prepared, ctx)
    val v = evalJsIsolatedAwait(ctx, prepared.code)
    if (v.isString) v.asString() else v.toString
  }

  override protected def parseJSONScript(
    varName: String,
    tempVarName: String
  ): String =
    s"const $varName = JSON.parse($tempVarName)"

  /**
   * Run the code inside an async function (top-level `await` works, and declarations stay local to
   * the call instead of becoming globals) and wait for the result. A failure is rethrown as the
   * original guest error — the code is executed exactly ONCE (the previous fallback re-ran a failing
   * script at top level, doubling its side effects and turning its `const`/`let` into permanent
   * globals of the pooled context).
   */
  private def evalJsIsolatedAwait(
    ctx: Context,
    code: String
  ): Value = {
    // bind the code so JS can eval it
    ctx.getBindings(language).putMember(CodeBindingName, code)

    // async IIFE so top-level `await` works inside the snippet
    // eval(__code) returns the completion value (last expression)
    val promise = ctx.eval(language, s"(async () => { return eval($CodeBindingName); })()")

    val fut = new CompletableFuture[Value]()
    val onFulfilled = new ProxyExecutable {
      override def execute(args: Value*): AnyRef = {
        fut.complete(args.headOption.getOrElse(Value.asValue(null))); null
      }
    }
    val onRejected = new ProxyExecutable {
      override def execute(args: Value*): AnyRef = {
        fut.completeExceptionally(new JsRejection(args.headOption.getOrElse(Value.asValue(null)))); null
      }
    }
    promise.invokeMember("then", onFulfilled, onRejected)

    try fut.get()
    catch {
      case ex: java.util.concurrent.ExecutionException =>
        ex.getCause match {
          case rejection: JsRejection =>
            // rethrow the guest's own error so the caller gets a PolyglotException with its message
            ctx.eval(language, "(function (e) { throw e; })").execute(rejection.reason)
            throw new IllegalStateException("unreachable: rethrowing a rejected promise's reason returned")
          case other => throw other
        }
    }
  }
}

private object GraalJsPool {

  /** Global binding holding the code of the current eval (removed after every use). */
  val CodeBindingName = "__code"

  /** Carries a rejected promise's reason back to the host so it can be rethrown as-is. */
  final class JsRejection(val reason: Value) extends RuntimeException("Promise rejected", null, false, false)
}
