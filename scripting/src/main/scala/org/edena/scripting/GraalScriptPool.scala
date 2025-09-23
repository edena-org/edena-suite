package org.edena.scripting

import akka.Done
import akka.actor.CoordinatedShutdown
import org.edena.core.util.LoggingSupport
import org.graalvm.polyglot._
import org.graalvm.polyglot.io.IOAccess
import play.api.libs.json.{JsValue, Json}

import java.util.concurrent.LinkedBlockingQueue
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

trait GraalScriptPool {

  /**
   * Evaluates the given script code and returns the result as a string.
   *
   * @param code
   *   The script code to execute in the target language
   * @param bindings
   *   Optional map of variable name-value pairs to bind in the script context
   * @return
   *   Either an error message (Left) or the string result of script execution (Right)
   */
  def evalToString(
    code: String,
    bindings: Map[String, Any] = Map.empty
  ): Either[String, String]

  /**
   * Evaluates the given script code that should return a JSON string, then parses it into a
   * JsValue.
   *
   * This is a convenience method that combines script execution with JSON parsing. The script
   * is expected to return a valid JSON string representation.
   *
   * @param code
   *   The script code to execute that should produce JSON output
   * @param bindings
   *   Optional map of variable name-value pairs to bind in the script context
   * @return
   *   Either an error message (Left) or the parsed JSON as JsValue (Right)
   */
  def evalToJson(
    code: String,
    bindings: Map[String, Any] = Map.empty
  ): Either[String, JsValue]

  /**
   * Returns the language identifier for this script pool (e.g., "js", "python").
   *
   * @return
   *   The GraalVM language identifier used by this pool
   */
  def language: String

  /**
   * Returns an optional description of this script pool's configuration and capabilities.
   *
   * This can include information about security settings, available bridges,
   * allowed operations, and other pool-specific configuration details.
   *
   * @return
   *   Optional descriptive text about this pool's setup and restrictions
   */
  def description: Option[String]

  /**
   * Close all contexts and the underlying engine; pool is no longer usable after this.
   */
  def close(): Unit
}

abstract class GraalScriptPoolImpl(
  val language: String,
  config: GraalPoolConfig,
  extendEngine: Option[Engine#Builder => Unit],
  extendContextBuilder: Option[(Context#Builder, Int) => Unit],
  extendContext: Option[Context => Unit],
  coordinatedShutdown: CoordinatedShutdown
)(
  implicit val ec: ExecutionContext
) extends GraalScriptPool
    with LoggingSupport {

  import config._

  private val DefaultPoolSize = math.max(1, Runtime.getRuntime.availableProcessors() / 2)

  private def actualPoolSize: Int = poolSize.getOrElse(DefaultPoolSize)

  protected def resetContext(ctx: Context): Unit
  protected def warmupContext(ctx: Context): Unit

  private lazy val engines: Seq[Engine] = createEngines()

  protected def createEngines(): Seq[Engine] = {
    def createAux: Engine = {
      val builder = Engine.newBuilder().allowExperimentalOptions(true)
      extendEngine.foreach(_(builder))
      builder.build()
    }

    if (config.enginePerContext)
      Seq.fill(actualPoolSize)(createAux)
    else
      Seq(createAux)
  }

  private val pool = new LinkedBlockingQueue[Context](actualPoolSize)

  // Initialize pool on construction
  init

  private def init = {
    logExecutionTime(
      s"Initialization of ${getClass.getSimpleName}${description.map(x => s" ($x)").getOrElse("")} with $actualPoolSize contexts/threads and ${engines.size} engines"
    ) {
      // Create and warm contexts
      val enginesToUse = engines.size match {
        case 0 =>
          throw new IllegalStateException("No GraalVM engines could be created")

        case 1 => Seq.fill(actualPoolSize)(engines.head)

        case n if (n == actualPoolSize) => engines

        case _ => throw new IllegalStateException(
          s"Number of engines (${engines.size}) must be 1 or equal to pool size ($actualPoolSize)"
        )
      }

      enginesToUse.zipWithIndex.foreach { case (engine, index) =>
        val builder = createContextBuilder()
          .engine(engine)
          .allowIO(allowIO)
          .allowHostAccess(allowHostAccess)
          //        .allowHostClassLookup((_: String) => false)
          //        .allowCreateThread(false)
          .allowExperimentalOptions(true)

        extendContextBuilder.foreach(_(builder, index))

        val ctx = builder.build()

        // Language-specific warmup
        warmupContext(ctx)
        pool.put(ctx)
      }
    }
  }

  protected def createContextBuilder(): Context#Builder =
    Context.newBuilder(language)

  /** Borrow a context, run f, convert/return, then (optionally) reset & return to pool. */
  protected def withContext[A](f: Context => A): A = {
    val ctx = pool.take()
    try f(ctx)
    finally {
      if (resetAfterEachUse.getOrElse(true)) {
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
        extendContext.foreach(_(ctx))
        evalCore(code, bindings, ctx)
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

  protected def evalCore(
    code: String,
    bindings: Map[String, Any] = Map.empty,
    ctx: Context
  ): String = {
    val languageBindings = ctx.getBindings(language)
    bindings.foreach { case (k, v) => languageBindings.putMember(k, v) }
    val v = ctx.eval(language, code)
    if (v.isString) v.asString() else v.toString
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

  override def description: Option[String] = config.description

  override def close(): Unit = {
    var c = pool.poll()
    while (c != null) {
      try c.close()
      finally c = pool.poll()
    }
    try engines.foreach(_.close())
    catch { case _: Throwable => () }
  }
}
