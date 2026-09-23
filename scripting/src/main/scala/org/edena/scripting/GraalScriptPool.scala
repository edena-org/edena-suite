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

/**
 * A pool of reusable GraalVM polyglot contexts for one language.
 *
 * ISOLATION — read before running untrusted or multi-tenant scripts:
 *  - POOLED mode (the default) reuses a context across callers. After every use the pool removes
 *    the injected bindings and restores the context's global state to a snapshot taken right after
 *    warmup: Python `__main__` globals, `builtins` and the pre-imported modules (`sys`, `json`,
 *    `datetime`); JavaScript `globalThis` and the core built-in objects/prototypes. This stops
 *    accidental carry-over, but it is NOT a security boundary: state reachable elsewhere survives
 *    (Python: modules imported by the script and their attributes, contents of mutable objects such as
 *    `sys.path`; JavaScript: top-level `let`/`const` created via indirect eval, other intrinsics).
 *  - ISOLATED mode runs the script in a brand-new context created for that call and closed afterwards
 *    (same engine and options as the pooled contexts), so nothing can carry over. Use it for untrusted
 *    input, per call via `isolated = true` or for a whole pool via [[GraalPoolConfig.freshContextPerUse]].
 *    It costs a context creation per eval (see the scripting tests for measured numbers).
 */
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
   * [[evalToString]] with an explicit isolation choice. With `isolated = true` the script runs in a
   * fresh context that is created for this call and closed afterwards, so it can neither see nor leave
   * behind any state (see the trait docs). `isolated = false` uses the pool's configured mode, i.e. it
   * cannot opt out of a pool configured with `freshContextPerUse`.
   */
  def evalToString(
    code: String,
    bindings: Map[String, Any],
    isolated: Boolean
  ): Either[String, String]

  /** [[evalToJson]] with an explicit isolation choice, see the isolated [[evalToString]]. */
  def evalToJson(
    code: String,
    bindings: Map[String, Any],
    isolated: Boolean
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
   * PREFER [[scheduleContextsRecycle]] for routine native-memory reclaim — it has no global
   * pause and cannot stall executions.
   */
  def recreateAllContexts(): Unit

  /**
   * Request a LAZY recycle of (up to) the whole pool: each of the next poolSize context returns
   * swaps its context for a freshly created one (creation bounded and abandonable; the old
   * context is closed only after the pool is whole again). Non-blocking, no exclusive phase —
   * a hung close/create can never stall script executions. Best-effort: failed swaps keep the
   * old context.
   */
  def scheduleContextsRecycle(): Unit

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

  /**
   * Snapshot the context's pristine global state (called once per pooled context, right after
   * warmup) and return a zero-argument guest function that restores it. The function is held only
   * on the host side, so guest code can neither reach nor replace it; see [[runRestorer]].
   */
  protected def createRestorer(ctx: Context): Value

  // host-held restore function per pooled context (fresh per-use contexts have none)
  private val restorers = new ConcurrentHashMap[Context, Value]()

  // contexts whose cleanup failed and could not be replaced: never serve an eval until replaced
  private val tainted = java.util.Collections.newSetFromMap(new ConcurrentHashMap[Context, java.lang.Boolean]())

  // observability (also used by the isolation tests)
  private val cleanupFailures = new java.util.concurrent.atomic.AtomicInteger(0)
  private val cleanupReplacements = new java.util.concurrent.atomic.AtomicInteger(0)
  private val taintedReturns = new java.util.concurrent.atomic.AtomicInteger(0)
  private[scripting] def cleanupFailuresCount: Int = cleanupFailures.get()
  private[scripting] def cleanupReplacementsCount: Int = cleanupReplacements.get()
  private[scripting] def taintedReturnsCount: Int = taintedReturns.get()
  private[scripting] def taintedCount: Int = tainted.size()

  // test-only fault injection (the failure paths cannot be triggered deterministically otherwise)
  @volatile private[scripting] var failCleanupForTesting: Boolean = false
  @volatile private[scripting] var failReplacementForTesting: Boolean = false

  /** Restore the context's global state to its post-warmup snapshot. */
  protected final def runRestorer(ctx: Context): Unit = {
    val restore = restorers.get(ctx)
    if (restore == null)
      throw new IllegalStateException("No state restorer registered for this script context.")
    restore.executeVoid()
  }

  /** Names bound on every eval besides the user's bindings (e.g. the JS code holder). */
  protected def internalBindingNames: Set[String] = Set.empty

  /** Remove everything an eval injected into the context's bindings (always runs after an eval). */
  protected def removeInjected(ctx: Context, names: Set[String]): Unit =
    if (names.nonEmpty) {
      val languageBindings = ctx.getBindings(language)
      names.foreach { name =>
        if (languageBindings.hasMember(name)) languageBindings.removeMember(name)
      }
    }

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
        pool.put((createAndWarm(createInfo, pooled = true), createInfo))
      }
    }
  }

  /** Create a context and run the language-specific warmup; pooled contexts additionally get their
   *  state restorer (snapshot of the warmed-up state). A context failing half-way is closed. */
  private def createAndWarm(info: ContextCreateInfo, pooled: Boolean): Context = {
    val ctx = createContext(info)
    try {
      warmupContext(ctx)
      if (pooled) restorers.put(ctx, createRestorer(ctx))
      ctx
    } catch {
      case e: Throwable =>
        forget(ctx)
        try ctx.close(true)
        catch { case _: Throwable => () }
        throw e
    }
  }

  /** Drop the pool's bookkeeping for a context that is being closed. */
  private def forget(ctx: Context): Unit = {
    restorers.remove(ctx)
    tainted.remove(ctx)
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

  /** How long a borrower waits for a free context before FAILING the eval (instead of parking
   *  forever — an unbounded take() turned any lost context into permanently stuck threads). */
  private val ContextAcquireTimeoutMins = 15L

  /** Borrow a pool slot, run f on a context, then clean up and return the slot.
   *
   *  HARDENED (2026-08-16, after an 11h pipeline deadlock) — the pool must NEVER
   *  shrink and cleanup must NEVER hang:
   *  - acquisition is BOUNDED: a wedged/lost context fails the eval with a clear error instead
   *    of parking the borrower forever;
   *  - the replace path catches Throwable (fatal errors must not shrink the pool either), creates
   *    the replacement with a bound, and closes the broken context on a daemon thread (GraalPy
   *    context close can hang on wedged native state).
   *
   *  ISOLATION (2026-09-23, cross-caller leak fix):
   *  - `injectedNames` (the eval's bindings) are ALWAYS removed after use, whether or not the pool
   *    resets after each use;
   *  - a context whose cleanup failed and could not be replaced goes back only to keep the pool
   *    size, marked TAINTED: its next borrow must replace it first and fails fast if that fails
   *    again, so a dirty context never serves another caller;
   *  - `isolated` (or [[GraalPoolConfig.freshContextPerUse]]) runs f on a brand-new context created
   *    for this call and closed afterwards; the slot is still borrowed so concurrency stays bounded
   *    by the pool size, and its pooled context is not touched. */
  protected def withContext[A](
    isolated: Boolean = false,
    injectedNames: Set[String] = Set.empty
  )(
    f: Context => A
  ): A = {
    val taken = pool.poll(ContextAcquireTimeoutMins, java.util.concurrent.TimeUnit.MINUTES)
    if (taken == null)
      throw new IllegalStateException(
        s"No script context became available within $ContextAcquireTimeoutMins min — " +
          "pool exhausted or a previous eval is wedged."
      )
    val (borrowed, createInfo) = taken
    val ctx = if (tainted.contains(borrowed)) replaceTainted(borrowed, createInfo) else borrowed

    if (isolated || freshContextPerUse)
      runOnFreshContext(ctx, createInfo, f)
    else
      runOnPooledContext(ctx, createInfo, injectedNames, f)
  }

  private def runOnPooledContext[A](
    ctx: Context,
    createInfo: ContextCreateInfo,
    injectedNames: Set[String],
    f: Context => A
  ): A = {
    extendContext.foreach(_(ctx))

    try f(ctx)
    finally {
      val cleaned =
        try {
          if (failCleanupForTesting) throw new IllegalStateException("cleanup failure injected for testing")
          removeInjected(ctx, injectedNames)
          if (resetAfterEachUse.getOrElse(true)) resetContext(ctx)
          true
        } catch {
          case ex: Throwable =>
            cleanupFailures.incrementAndGet()
            logger.warn("Error cleaning up script context after use, replacing it", ex)
            false
        }

      if (cleaned)
        returnOrLazilyRecycle(ctx, createInfo)
      else
        createReplacementBounded(createInfo) match {
          case Some(newCtx) =>
            cleanupReplacements.incrementAndGet()
            pool.put((newCtx, createInfo)) // pool whole again first ...
            closeInBackground(ctx) // ... then drop the dirty one
          case None =>
            taintedReturns.incrementAndGet()
            logger.error(
              "Failed to create a replacement script context — returning the dirty original TAINTED to keep " +
                "the pool size: it serves no eval until replaced (its next borrow retries the replacement " +
                "and fails fast if that fails again)."
            )
            try removeInjected(ctx, injectedNames)
            catch { case e: Throwable => logger.warn(s"Removing injected bindings from a tainted context failed: ${e.getMessage}") }
            tainted.add(ctx)
            pool.put((ctx, createInfo))
        }
    }
  }

  private def runOnFreshContext[A](
    slotCtx: Context,
    createInfo: ContextCreateInfo,
    f: Context => A
  ): A = {
    val fresh =
      try createFreshBounded(createInfo)
      catch {
        case e: Throwable =>
          returnOrLazilyRecycle(slotCtx, createInfo)
          throw e
      }

    fresh match {
      case None =>
        returnOrLazilyRecycle(slotCtx, createInfo)
        throw new IllegalStateException(
          s"Could not create a fresh isolated script context (language $language) within $replacementCreateTimeoutMs ms."
        )

      case Some(ctx) =>
        try {
          extendContext.foreach(_(ctx))
          f(ctx)
        } finally {
          returnOrLazilyRecycle(slotCtx, createInfo) // untouched pooled context goes straight back
          closeInBackground(ctx)
        }
    }
  }

  /** A tainted context (dirty, unreplaced) must be replaced before it may serve an eval. */
  private def replaceTainted(ctx: Context, createInfo: ContextCreateInfo): Context =
    createReplacementBounded(createInfo) match {
      case Some(newCtx) =>
        cleanupReplacements.incrementAndGet()
        closeInBackground(ctx)
        logger.info("Replaced a tainted script context.")
        newCtx
      case None =>
        pool.put((ctx, createInfo))
        throw new IllegalStateException(
          s"The script context (language $language) is tainted — its cleanup failed and no replacement " +
            "could be created — so it will not run this eval. Retry later."
        )
    }

  /** Close a context that is out of the pool for good, without making the caller wait. */
  private def closeInBackground(ctx: Context): Unit = {
    forget(ctx)
    val closer = new Thread(
      () =>
        try ctx.close(true)
        catch { case e: Throwable => logger.warn(s"Script context close failed: ${e.getMessage}") },
      "graal-context-closer"
    )
    closer.setDaemon(true)
    closer.start()
  }

  // ---- LAZY context recycling (native-memory reclaim without a global pause) -----------------
  // A stop-the-world recreateAllContexts under an exclusive lock WEDGED a production pipeline
  // three times: context close AND fresh-context creation can hang indefinitely on poisoned
  // native state, and any global exclusive phase turns that hang into a full stop. The lazy
  // scheme has no exclusive phase at all: after scheduleContextsRecycle(), the next poolSize
  // context RETURNS each swap their context one-by-one — replacement is created FIRST (bounded,
  // abandonable), the pool is made whole, and only then is the old context closed (bounded).
  // Failure/timeout of a replacement keeps the old context; the attempt is simply dropped
  // (best-effort — the next scheduled recycle retries). The pool size is invariant on every path.

  private val pendingContextRecycles = new java.util.concurrent.atomic.AtomicInteger(0)

  /** How long a fresh replacement context (creation + warmup) may take before the swap is
   *  abandoned and the old context is kept. */
  protected val replacementCreateTimeoutMs: Long = 120000L

  /** Request a lazy recycle of (up to) the whole pool: each of the next `poolSize` context
   *  returns replaces its context. Non-blocking; safe to call at any time; repeated calls do
   *  not stack beyond the pool size. */
  def scheduleContextsRecycle(): Unit = {
    pendingContextRecycles.set(actualPoolSize)
    logger.info(
      s"Lazy context recycle scheduled: the next $actualPoolSize context return(s) will swap their contexts."
    )
  }

  private[scripting] def pendingRecyclesCount: Int = pendingContextRecycles.get()

  private def returnOrLazilyRecycle(ctx: Context, createInfo: ContextCreateInfo): Unit =
    if (pendingContextRecycles.getAndDecrement() > 0) {
      createReplacementBounded(createInfo) match {
        case Some(newCtx) =>
          pool.put((newCtx, createInfo))
          closeBounded(ctx) // old context closed only AFTER the pool is whole again
          logger.info(s"Lazy context recycle: swapped one context (${pendingContextRecycles.get()} pending).")
        case None =>
          // best-effort: keep the old context, drop this attempt (next schedule retries)
          logger.warn("Lazy context recycle: replacement creation failed/timed out — keeping the old context.")
          pool.put((ctx, createInfo))
      }
    } else
      pool.put((ctx, createInfo))

  /** Create + warm up a replacement context on a daemon thread, waiting a bounded time. On
   *  timeout the creation is abandoned (daemon thread; a late-completing context is closed by
   *  the creator itself — never leaked, never double-used). */
  private def createReplacementBounded(createInfo: ContextCreateInfo): Option[Context] =
    if (failReplacementForTesting) None
    else createBounded(createInfo, pooled = true)

  /** A per-use context for isolated evals: same engine/options/warmup, no state snapshot. */
  private def createFreshBounded(createInfo: ContextCreateInfo): Option[Context] =
    createBounded(createInfo, pooled = false)

  private def createBounded(createInfo: ContextCreateInfo, pooled: Boolean): Option[Context] = {
    val abandoned = new java.util.concurrent.atomic.AtomicBoolean(false)
    val resultRef = new java.util.concurrent.atomic.AtomicReference[Context](null)
    val creator = new Thread(
      () =>
        try {
          val c = createAndWarm(createInfo, pooled)
          resultRef.set(c)
          // the waiter may have given up between our set and its final check
          if (abandoned.get() && resultRef.compareAndSet(c, null)) {
            forget(c)
            try c.close(true)
            catch { case e: Throwable => logger.warn(s"Late replacement context close failed: ${e.getMessage}") }
          }
        } catch {
          case e: Throwable => logger.warn(s"Replacement context creation failed: ${e.getMessage}")
        },
      "graal-context-replacer"
    )
    creator.setDaemon(true)
    creator.start()
    creator.join(replacementCreateTimeoutMs)
    Option(resultRef.getAndSet(null)).orElse {
      abandoned.set(true)
      if (creator.isAlive)
        logger.warn(
          s"Replacement context creation still running after ${replacementCreateTimeoutMs} ms — abandoned on a daemon thread."
        )
      // second chance: the creator may have finished between the join timeout and the flag
      Option(resultRef.getAndSet(null))
    }
  }

  /** Close a (possibly wedged) context without ever hanging the caller: run the close on a
   *  daemon thread and wait a bounded time — an unfinished close is abandoned (daemon thread,
   *  logged) rather than blocking pool recovery. */
  private def closeBounded(ctx: Context): Unit = {
    forget(ctx)
    val closer = new Thread(
      () =>
        try ctx.close(true)
        catch { case e: Throwable => logger.warn(s"Script context close failed: ${e.getMessage}") },
      "graal-context-closer"
    )
    closer.setDaemon(true)
    closer.start()
    closer.join(15000)
    if (closer.isAlive)
      logger.warn("Script context close did not finish within 15s — abandoned on a daemon thread.")
  }

  def evalToString(
    code: String,
    bindings: Map[String, Any] = Map.empty
  ): Either[String, String] =
    evalToString(code, bindings, isolated = false)

  def evalToString(
    code: String,
    bindings: Map[String, Any],
    isolated: Boolean
  ): Either[String, String] = {
    val prepared = prepare(code, bindings)
    withContext(isolated, prepared.injectedNames) { ctx =>
      Try {
        evalCore(prepared, ctx)
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
  }

  /** Evaluate script that returns JSON string; parse into JsValue. */
  def evalToJson(
    code: String,
    bindings: Map[String, Any] = Map.empty
  ): Either[String, JsValue] =
    evalToJson(code, bindings, isolated = false)

  def evalToJson(
    code: String,
    bindings: Map[String, Any],
    isolated: Boolean
  ): Either[String, JsValue] =
    evalToString(code, bindings, isolated).flatMap { s =>
      Try(Json.parse(s)).fold(
        ex => Left(s"JSON parsing error: ${ex.getMessage}"),
        json => Right(json)
      )
    }

  /**
   * An eval ready to run: the final code (JSON-parsing prefix lines + user code), the bindings to
   * inject, and every name the eval may leave in the context's bindings — the injected bindings,
   * the variables the prefix lines define, and [[internalBindingNames]] — all removed after use.
   */
  protected case class PreparedEval(
    code: String,
    bindings: Map[String, Any],
    injectedNames: Set[String]
  )

  protected def prepare(
    code: String,
    bindings: Map[String, Any]
  ): PreparedEval = {
    val (processedBindings, codePrefix) = processJsonInputs(bindings)
    val parsedVariables = bindings.keySet.filterNot(processedBindings.contains)
    PreparedEval(
      if (codePrefix.nonEmpty) codePrefix + "\n" + code else code,
      processedBindings,
      processedBindings.keySet ++ parsedVariables ++ internalBindingNames
    )
  }

  protected def putBindings(
    prepared: PreparedEval,
    ctx: Context
  ): Unit = {
    val languageBindings = ctx.getBindings(language)
    prepared.bindings.foreach { case (k, v) => languageBindings.putMember(k, v) }
  }

  protected def evalCore(
    prepared: PreparedEval,
    ctx: Context
  ): String = {
    putBindings(prepared, ctx)
    val v = ctx.eval(language, prepared.code)
    if (v.isString) v.asString() else v.toString
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

    // Swap each context with the same bounded, never-shrink ordering as the lazy path:
    // replacement FIRST (bounded, abandonable), pool made whole, old context closed after
    // (bounded). A hung close/create previously held this method — and any lock around it —
    // forever (2026-08-16 prod wedge #3); a failed swap now keeps the old context instead.
    var swapped = 0
    contextsToRecreate.foreach { case (ctx, createInfo) =>
      createReplacementBounded(createInfo) match {
        case Some(newCtx) =>
          pool.put((newCtx, createInfo))
          closeBounded(ctx)
          swapped += 1
        case None =>
          logger.warn("Context recreation: replacement creation failed/timed out — keeping the old context.")
          pool.put((ctx, createInfo))
      }
    }

    val elapsedMs = System.currentTimeMillis() - startTime
    logger.info(s"Recreated $swapped of ${contextsToRecreate.size} contexts in ${elapsedMs} ms")
  }

  override def close(): Unit = {
    var c = pool.poll()
    while (c != null) {
      forget(c._1)
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
