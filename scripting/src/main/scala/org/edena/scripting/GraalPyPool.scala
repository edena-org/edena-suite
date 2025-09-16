package org.edena.scripting

import akka.actor.CoordinatedShutdown
import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits.ConfigExt
import org.graalvm.polyglot._

import javax.inject._
import scala.concurrent.ExecutionContext

final private class GraalPyPool @Inject() (
  config: Config,
  coordinatedShutdown: CoordinatedShutdown
)(
  implicit ec: ExecutionContext
) extends GraalScriptPoolImpl(
  language = "python",
  poolSize = config.optionalInt("graalvm.python.pool_size"),
  resetAfterEachUse = config.optionalBoolean("graalvm.python.reset_after_each_use").getOrElse(true),
  coordinatedShutdown
) {

  // warm modules
  private lazy val preImport: Seq[String] = Seq("sys", "json", "datetime")

  // Implement abstract methods from GraalScriptPool
  protected def resetContext(ctx: Context): Unit = {
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
}
