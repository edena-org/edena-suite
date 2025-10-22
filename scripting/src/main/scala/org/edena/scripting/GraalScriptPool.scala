package org.edena.scripting

import akka.Done
import akka.actor.CoordinatedShutdown
import org.edena.core.util.LoggingSupport
import org.graalvm.polyglot._
import org.graalvm.polyglot.io.IOAccess
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import java.io.PrintStream
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue}
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters._

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
   * This can include information about security settings, available bridges, allowed
   * operations, and other pool-specific configuration details.
   *
   * @return
   *   Optional descriptive text about this pool's setup and restrictions
   */
  def description: Option[String]

  /**
   * Recreates all contexts in the pool by closing existing ones and creating fresh replacements.
   *
   * This operation is useful when contexts have become corrupted, are in an inconsistent state,
   * or when you need to apply updated configuration. The method blocks until all contexts have
   * been recreated and returned to the pool.
   *
   * Note: This is a blocking operation that temporarily reduces available contexts during
   * recreation. Concurrent script executions may block waiting for available contexts.
   */
  def recreateAllContexts(): Unit

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

  private val pool = new LinkedBlockingQueue[(Context, ContextCreateInfo)](actualPoolSize)

  // Initialize pool on construction
  init

  private def init = {
    logExecutionTime(
      s"Initialization of ${getClass.getSimpleName}${description
          .map(x => s" ($x)")
          .getOrElse("")} with $actualPoolSize contexts/threads and ${engines.size} engines"
    ) {

      // Create and warm contexts
      val enginesToUse = engines.size match {
        case 0 =>
          throw new IllegalStateException("No GraalVM engines could be created")

        case 1 => Seq.fill(actualPoolSize)(engines.head)

        case n if (n == actualPoolSize) => engines

        case _ =>
          throw new IllegalStateException(
            s"Number of engines (${engines.size}) must be 1 or equal to pool size ($actualPoolSize)"
          )
      }

      val outOs = new LoggerOutputStream("GraalConsoleOut")
      val errOs = new LoggerOutputStream("GraalConsoleErr", isErr = true)

      enginesToUse.zipWithIndex.foreach { case (engine, index) =>
        val createInfo = ContextCreateInfo(engine, index, Some(outOs), Some(errOs))
        val ctx = createContext(createInfo)
        // Language-specific warmup
        warmupContext(ctx)
        pool.put((ctx, createInfo))
      }
    }
  }

  private def createContext(
    info: ContextCreateInfo
  ): Context = {
    import info._

    val builder = createContextBuilder()
      .engine(engine)
      .allowIO(allowIO)
      .allowHostAccess(allowHostAccess)
      //        .allowHostClassLookup((_: String) => false)
      //        .allowCreateThread(false)
      .allowExperimentalOptions(true)

    outOs.foreach(outOs => builder.out(new PrintStream(outOs, true, "UTF-8")))
    errOs.foreach(errOs => builder.err(new PrintStream(errOs, true, "UTF-8")))

    extendContextBuilder.foreach(_(builder, index))
    builder.build()
  }

  protected def createContextBuilder(): Context#Builder =
    Context.newBuilder(language)

  /** Borrow a context, run f, convert/return, then (optionally) reset & return to pool. */
  protected def withContext[A](f: Context => A): A = {
    val (ctx, createInfo) = pool.take()
//    if (ctx.getPolyglotBindings.getMember("fsBridge") == null) {
//      println(">>> Adding bridges to context")
    extendContext.foreach(_(ctx))

    try f(ctx)
    finally {
      if (resetAfterEachUse.getOrElse(true)) {
        try {
          resetContext(ctx)
          pool.put((ctx, createInfo))
        }
        catch {
          case NonFatal(ex) =>
            logger.warn("Error resetting script context, discarding it", ex)
            // discard context on error
            ctx.close()
            // replace with a new one
            val newCtx = createContext(createInfo)
            // Language-specific warmup
            warmupContext(newCtx)
            pool.put((newCtx, createInfo))
        }
      } else {
        // just return to pool without reset
        pool.put((ctx, createInfo))
      }
    }
  }

  def evalToString(
    code: String,
    bindings: Map[String, Any] = Map.empty
  ): Either[String, String] =
    withContext { ctx =>
      Try {
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
    val finalCode = substituteBindings(code, bindings, ctx)
    val v = ctx.eval(language, finalCode)
    if (v.isString) v.asString() else v.toString
  }

  protected def substituteBindings(
    code: String,
    bindings: Map[String, Any],
    ctx: Context
  ) = {
    val languageBindings = ctx.getBindings(language)

    val (processedBindings, codePrefix) = processJsonInputs(bindings)
    processedBindings.foreach { case (k, v) => languageBindings.putMember(k, v) }
    if (codePrefix.nonEmpty) codePrefix + "\n" + code else code
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

  /**
   * Process bindings to handle JSON strings.
   * @param bindings
   * @return
   *   new bindings map and code prefix to parse JSON strings
   */
  protected def processJsonInputs(bindings: Map[String, Any]): (Map[String, Any], String) = {
    val newBindingsAndCodePrefixes = bindings.toSeq.map { case (k, v) =>
      def processForString(s: String) = {
        val randomSuffix = Random.nextInt(1000000)
        val tempVarName = s"${k}_string_$randomSuffix"
        val newBinding = (tempVarName -> s)
        val newCodeLine = parseJSONScript(k, tempVarName)
        (newBinding, Some(newCodeLine))
      }

      v match {
        case s: String if isJsonString(s) =>
          processForString(s)

        case s: JsObject =>
          processForString(Json.stringify(s))

        case s: JsArray =>
          processForString(Json.stringify(s))

        case other =>
          (k -> other, None)
      }
    }

    val jsonBindings = newBindingsAndCodePrefixes.map(_._1).toMap
    val codeLines = newBindingsAndCodePrefixes.flatMap(_._2)
    (jsonBindings, codeLines.mkString("\n"))
  }

  protected def parseJSONScript(
    varName: String,
    tempVarName: String
  ): String =
    s"const $varName = JSON.parse($tempVarName)"

  private def isJsonString(s: String): Boolean = {
    val trimmed = s.trim
    val isValidJsonStructure = (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
      (trimmed.startsWith("[") && trimmed.endsWith("]"))

    if (isValidJsonStructure) {
      try {
        Json.parse(s)
        true
      } catch {
        case e: Exception => false
      }
    } else
      false
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

  override def recreateAllContexts(): Unit = {
    logger.info(s"Recreating all contexts in ${getClass.getSimpleName} ($language - ${description.getOrElse("N/A")}))")

    val startTime = System.currentTimeMillis()
    val contextsToRecreate = scala.collection.mutable.ArrayBuffer[(Context, ContextCreateInfo)]()

    // Drain all contexts from the pool
    var entry = pool.poll()
    while (entry != null) {
      contextsToRecreate += entry
      entry = pool.poll()
    }

    // Close old contexts and create new ones
    contextsToRecreate.foreach { case (ctx, createInfo) =>
      try ctx.close()
      catch { case NonFatal(e) => logger.warn(s"Error closing context during recreation: ${e.getMessage}") }

      val newCtx = createContext(createInfo)
      warmupContext(newCtx)
      pool.put((newCtx, createInfo))
    }

    val elapsedMs = System.currentTimeMillis() - startTime
    logger.info(s"Successfully recreated ${contextsToRecreate.size} contexts in ${elapsedMs} ms")
  }

  override def close(): Unit = {
    var c = pool.poll()
    while (c != null) {
      try c._1.close()
      finally c = pool.poll()
    }
    try engines.foreach(_.close())
    catch { case _: Throwable => () }
  }
}

case class ContextCreateInfo(
  engine: Engine,
  index: Int,
  outOs: Option[LoggerOutputStream],
  errOs: Option[LoggerOutputStream]
)
