package org.edena.scripting

import akka.Done
import akka.actor.CoordinatedShutdown
import org.edena.core.util.LoggingSupport
import org.graalvm.polyglot._
import org.graalvm.polyglot.io.IOAccess
import play.api.libs.json.{JsValue, Json}

import java.util.concurrent.LinkedBlockingQueue
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

trait GraalScriptPool {

  /**
   * Evaluates the given script code and returns the result as a string.
   *
   * @param code The script code to execute in the target language
   * @param bindings Optional map of variable name-value pairs to bind in the script context
   * @return Either an error message (Left) or the string result of script execution (Right)
   */
  def evalToString(
    code: String,
    bindings: Map[String, Any] = Map.empty
  ): Either[String, String]

  /**
   * Evaluates the given script code that should return a JSON string, then parses it into a JsValue.
   *
   * This is a convenience method that combines script execution with JSON parsing. The script
   * is expected to return a valid JSON string representation.
   *
   * @param code The script code to execute that should produce JSON output
   * @param bindings Optional map of variable name-value pairs to bind in the script context
   * @return Either an error message (Left) or the parsed JSON as JsValue (Right)
   */
  def evalToJson(
    code: String,
    bindings: Map[String, Any] = Map.empty
  ): Either[String, JsValue]
}

abstract class GraalScriptPoolImpl(
  val language: String,
  poolSize: Option[Int],
  resetAfterEachUse: Boolean,
  coordinatedShutdown: CoordinatedShutdown
)(
  implicit val ec: ExecutionContext
) extends GraalScriptPool with LoggingSupport {

  private val DefaultPoolSize = math.max(1, Runtime.getRuntime.availableProcessors() / 2)

  private def actualPoolSize: Int = poolSize.getOrElse(DefaultPoolSize)

  protected def resetContext(ctx: Context): Unit
  protected def warmupContext(ctx: Context): Unit

  // Common implementation
  private val engine: Engine = Engine.newBuilder().build()
  private val pool = new LinkedBlockingQueue[Context](actualPoolSize)

  // Initialize pool on construction
  init

  private def init = logExecutionTime(
    s"Initialization of ${getClass.getSimpleName} with $actualPoolSize contexts/threads"
  ) {
    // Create and warm contexts
    (1 to actualPoolSize).foreach { _ =>
      val ctx = Context
        .newBuilder(language)
        .engine(engine)
        .allowIO(IOAccess.ALL)
        .allowHostAccess(HostAccess.ALL)
        .allowExperimentalOptions(true)
        .build()

      // Language-specific warmup
      warmupContext(ctx)
      pool.put(ctx)
    }
  }

  /** Borrow a context, run f, convert/return, then (optionally) reset & return to pool. */
  protected def withContext[A](f: Context => A): A = {
    val ctx = pool.take()
    try f(ctx)
    finally {
      if (resetAfterEachUse) {
        try resetContext(ctx)
        catch { case NonFatal(_) => /* best-effort */ }
      }
      pool.put(ctx)
    }
  }

  def evalToString(
    code: String,
    bindings: Map[String, Any] = Map.empty
  ): Either[String, String] =
    withContext { ctx =>
      Try {
        val languageBindings = ctx.getBindings(language)
        bindings.foreach { case (k, v) => languageBindings.putMember(k, v) }
        val v = ctx.eval(language, code)
        if (v.isString) v.asString() else v.toString
      }.fold(
        {
          case pe: PolyglotException =>
            val errorMsg = s"Script execution error (language $language): ${pe.getMessage}"
            logger.error(errorMsg)
            Left(errorMsg)
          case t =>
            val errorMsg =
              s"Unexpected script execution error (language $language): ${t.getMessage}"
            logger.error(errorMsg, t)
            Left(errorMsg)
        },
        result => Right(result)
      )
    }

  /** Evaluate script that returns JSON string; parse into JsValue. */
  def evalToJson(
    code: String,
    bindings: Map[String, Any] = Map.empty
  ): Either[String, JsValue] = {
    evalToString(code, bindings).flatMap { s =>
      Try(Json.parse(s)).fold(
        ex => Left(s"JSON parsing error: ${ex.getMessage}"),
        json => Right(json)
      )
    }
  }

  // Clean shutdown
  coordinatedShutdown.addTask(
    CoordinatedShutdown.PhaseServiceUnbind,
    s"${getClass.getSimpleName.toLowerCase}-shutdown"
  ) { () =>
    Future {
      close()
      Done
    }
  }

  def close(): Unit = {
    var c = pool.poll()
    while (c != null) {
      try c.close()
      finally c = pool.poll()
    }
    try engine.close()
    catch { case _: Throwable => () }
  }
}
