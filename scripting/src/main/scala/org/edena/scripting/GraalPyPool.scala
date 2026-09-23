package org.edena.scripting

import akka.actor.CoordinatedShutdown
import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits.ConfigExt
import org.graalvm.polyglot._

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala

final private class GraalPyPoolFactoryImpl @Inject() (
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
        appConfig.optionalInt("graalvm.python.pool_size")
      ),
      resetAfterEachUse = config.resetAfterEachUse.orElse(
        appConfig.optionalBoolean("graalvm.python.reset_after_each_use")
      ),
      freshContextPerUse = config.freshContextPerUse ||
        appConfig.optionalBoolean("graalvm.python.fresh_context_per_use").getOrElse(false)
    )

    val useNewReset = appConfig.optionalBoolean("graalvm.python.use_new_reset").getOrElse(true)

    new GraalPyPool(
      configFinal,
      extendEngine,
      extendContextBuilder,
      extendContext,
      coordinatedShutdown,
      useNewReset
    )
  }
}

final private class GraalPyPool(
  config: GraalPoolConfig,
  extendEngine: Option[Engine#Builder => Unit],
  extendContextBuilder: Option[(Context#Builder, Int) => Unit],
  extendContext: Option[Context => Unit],
  coordinatedShutdown: CoordinatedShutdown,
  useNewReset: Boolean
)(
  implicit ec: ExecutionContext
) extends GraalScriptPoolImpl(
      language = "python",
      config,
      extendEngine,
      extendContextBuilder,
      extendContext,
      coordinatedShutdown
    ) {

  // warm modules
  private lazy val preImport: Seq[String] = Seq("sys", "json", "datetime")

  // Python built-in functions that cannot be removed from bindings
  private val pythonBuiltins: Set[String] = Set(
    "abs", "all", "any", "ascii", "bin", "bool", "bytearray", "bytes",
    "callable", "chr", "classmethod", "compile", "complex", "delattr",
    "dict", "dir", "divmod", "enumerate", "eval", "exec", "filter",
    "float", "format", "frozenset", "getattr", "globals", "hasattr",
    "hash", "help", "hex", "id", "input", "int", "isinstance", "issubclass",
    "iter", "len", "list", "locals", "map", "max", "memoryview", "min",
    "next", "object", "oct", "open", "ord", "pow", "print", "property",
    "range", "repr", "reversed", "round", "set", "setattr", "slice",
    "sorted", "staticmethod", "str", "sum", "super", "tuple", "type",
    "vars", "zip", "__import__"
  )

  // Implement abstract methods from GraalScriptPool
  protected def resetContext(ctx: Context): Unit = {
    if (useNewReset) {
      runRestorer(ctx)
    } else {
      resetContextOld(ctx)
    }
  }

  /**
   * Snapshot `builtins`, `__main__` globals and the pre-imported modules right after warmup, and
   * return a guest function restoring them (each dict is reset to exactly its snapshot).
   * It captures everything it needs at creation time, so a script that sabotages builtins (e.g.
   * `builtins.list = None`, `builtins.globals = ...`) cannot break or subvert the reset. Module
   * CONTENTS (e.g. `sys.path` items) and modules the script imports itself are not restored.
   */
  override protected def createRestorer(ctx: Context): Value = {
    val moduleNames = preImport.map(s => s"'$s'").mkString(", ")
    ctx.eval(
      language,
      s"""def __edena_make_restorer():
         |    import builtins, sys
         |    _list, _get_module = list, sys.modules.get
         |    targets = [builtins.__dict__, sys.modules['__main__'].__dict__]
         |    for name in ($moduleNames,):
         |        module = _get_module(name)
         |        if module is not None:
         |            targets.append(module.__dict__)
         |    snapshots = []
         |    for d in targets:
         |        snapshot = dict(d)
         |        snapshot.pop('__edena_make_restorer', None)
         |        snapshots.append(snapshot)
         |    pairs = _list(zip(targets, snapshots))
         |    def restore():
         |        # clear + update leaves each dict exactly equal to its snapshot, in native code (a
         |        # per-key Python loop costs ~10 ms per eval when GraalPy runs without a JIT)
         |        for d, snapshot in pairs:
         |            d.clear()
         |            d.update(snapshot)
         |    return restore
         |""".stripMargin
    )
    val restorer = ctx.eval(language, "__edena_make_restorer()")
    ctx.eval(language, "del __edena_make_restorer")
    restorer
  }

  // Old implementation (graalvm.python.use_new_reset = false): removes bindings, then deletes the
  // non-preserved globals. Runs inside a function so it leaves no loop variables behind (at module
  // level, deleting its own `g`/`preserved` aborted the loop half-way and let data survive).
  private def resetContextOld(ctx: Context): Unit = {
    // Clean up old bindings, but skip Python built-in functions
    val bindings = ctx.getBindings(language)
    val existingKeys = bindings.getMemberKeys.asScala.toSet

    // Filter out Python built-ins and special attributes that cannot be removed
    val keysToRemove = existingKeys.filterNot { key =>
      pythonBuiltins.contains(key) ||
      key.startsWith("__") || // Python special attributes
      key == "None" || key == "True" || key == "False" // Python constants
    }

    keysToRemove.foreach { key =>
      try {
        bindings.removeMember(key)
      } catch {
        case _: UnsupportedOperationException =>
          // This shouldn't happen now, but keep as safety net
          logger.debug(s"Could not remove binding '$key' (this is usually harmless)")
      }
    }

    val preservedModules = preImport.map(s => s"'$s'").mkString(", ")
    val reset =
      s"""def __edena_reset():
         |    g = globals()
         |    preserved = {'__name__', '__doc__', '__package__', '__loader__', '__spec__', '__builtins__', $preservedModules}
         |    for k in list(g.keys()):
         |        if k not in preserved:
         |            del g[k]
         |__edena_reset()
         |""".stripMargin
    ctx.eval(language, reset)
  }

  protected def warmupContext(ctx: Context): Unit = {
    // Warmup (imports compile paths)
    preImport.foreach(m => ctx.eval(language, s"import $m"))
  }

  override protected def parseJSONScript(
    varName: String,
    tempVarName: String
  ): String =
    s"$varName = json.loads($tempVarName)"

//  // needed for IsolateNativeModules = true / native access but doesn't quite work
//  override protected def createContextBuilder(): Context#Builder =
//    GraalPyResources.contextBuilder()
}
