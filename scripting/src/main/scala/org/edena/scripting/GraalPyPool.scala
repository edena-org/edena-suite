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
      )
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
      resetContextNew(ctx)
    } else {
      resetContextOld(ctx)
    }
  }

  // New implementation: Python-only cleanup (safer, avoids context corruption)
  private def resetContextNew(ctx: Context): Unit = {
    val preservedModules = preImport.map(s => s"'$s'").mkString(", ")
    val reset =
      s"""g = globals()
         |preserved = {'__name__', '__doc__', '__package__', '__loader__', '__spec__', '__builtins__', $preservedModules}
         |for k in list(g.keys()):
         |    if k not in preserved:
         |        try:
         |            del g[k]
         |        except:
         |            pass
         |""".stripMargin
    ctx.eval(language, reset)
  }

  // Old implementation: Two-phase cleanup (may cause context corruption)
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
      s"""g = globals()
         |preserved = {'__name__', '__doc__', '__package__', '__loader__', '__spec__', '__builtins__', $preservedModules}
         |for k in list(g.keys()):
         |    if k not in preserved:
         |        del g[k]
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
