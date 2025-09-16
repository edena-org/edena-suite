package org.edena.scripting

import akka.actor.CoordinatedShutdown
import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits.ConfigExt
import org.graalvm.polyglot._

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
  resetAfterEachUse = config.optionalBoolean("graalvm.js.reset_after_each_use").getOrElse(true),
  coordinatedShutdown
) {

  // Common JavaScript globals to preserve during reset
  private val preservedGlobals: Seq[String] = Seq(
    "console", "JSON", "Object", "Array", "String", "Number", "Boolean",
    "Date", "Math", "parseInt", "parseFloat", "isNaN", "isFinite",
    "setTimeout", "setInterval", "clearTimeout", "clearInterval"
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
}