package org.edena.scripting

import net.codingwell.scalaguice.ScalaModule
import com.google.inject.Provides
import com.typesafe.config.Config
import org.edena.core.util.LoggingSupport
import org.edena.scripting.bridge.{FsBridge, HttpBridge}
import org.graalvm.polyglot.{Context, Engine, HostAccess, PolyglotAccess}
import org.graalvm.polyglot.io.IOAccess

import java.io.InputStream
import java.nio.file.{Files, Paths}
import javax.inject.{Inject, Provider, Singleton}
import scala.jdk.CollectionConverters._

final class GraalVmAdminModule extends ScalaModule with JsAdminPoolHelper {

  override def configure(): Unit = {
    bind(classOf[WarmUpAdmins]).asEagerSingleton()
  }

  @Provides @Singleton @JsAdmin
  def provideAdminJsPool(
    @JsPoolFactory factory: GraalPoolFactory,
    config: Config
  ): GraalScriptPool =
    provideAdminJsPoolCustom(
      factory,
      config,
      description = "admin - full access"
    )

  @Provides @Singleton @PyAdmin
  def provideAdminPyPool(
    @PyPoolFactory factory: GraalPoolFactory,
    config: Config
  ): GraalScriptPool = {
    val venvRoot = config.getString("graalvm.python.admin.venvs_root")

    logger.info(s"Creating Python admin-access pool - venv root folder: '${venvRoot}'")

    factory(
      GraalPoolConfig(
        allowIO = IOAccess.ALL,
        allowHostAccess = HostAccess.NONE,
        // seems like it's not needed
        enginePerContext = false,
        description = Some("admin - full access")
      ),
      extendEngine = Some { (eb: Engine#Builder) =>
        eb.option("python.IsolateNativeModules", "true")
      },
      extendContextBuilder = Some { (ctxb: Context#Builder, index: Int) =>
        ctxb.allowCreateThread(true)
        ctxb.allowNativeAccess(true)
        ctxb.allowCreateProcess(true)
        ctxb.option("python.SysPrefix", venvRoot + s"/ctx-${index + 1}")
      }
    )
  }
}

final class WarmUpAdmins @Inject() (
  @JsAdmin js: Provider[GraalScriptPool],
  @PyAdmin py: Provider[GraalScriptPool]
) { js.get(); py.get() }

trait JsAdminPoolHelper extends LoggingSupport {

  private val tempShimFolder = "js-shims"
  private val jsShimsResourcePath = "js-shims/node_modules"

  def provideAdminJsPoolCustom(
    factory: GraalPoolFactory,
    config: Config,
    description: String,
    extraBridges: Map[String, Any] = Map.empty
  ): GraalScriptPool = {
    val jsShimFoldersToCopy =
      config.getStringList("graalvm.js.admin.js_shim_folders").asScala.toSeq
    val allowedHosts =
      config.getStringList("graalvm.js.admin.fetch.allowed_hosts").asScala.toSet
    val allowedRoots = config
      .getStringList("graalvm.js.admin.fs.allowed_roots")
      .asScala
      .map(path => Paths.get(path).toAbsolutePath.normalize)
      .toSet
    val fsReadOnly = config.getBoolean("graalvm.js.admin.fs.read_only")

    logger.info(
      s"Creating JS admin-access pool - js shim folders: ${jsShimFoldersToCopy.mkString(", ")}, allowed fetch hosts: ${allowedHosts
          .mkString(", ")}, fs roots: ${allowedRoots.mkString(", ")}, fs read_only: $fsReadOnly"
    )

    provideFullAccessJsPool(
      factory,
      jsShimFoldersToCopy,
      description,
      bridges = Map(
        "httpBridge" -> new HttpBridge(allowedHosts),
        "fsBridge" -> new FsBridge(allowedRoots, readOnly = fsReadOnly)
      ) ++ extraBridges
    )
  }

  def provideFullAccessJsPool(
    factory: GraalPoolFactory,
    jsShimFoldersToCopy: Seq[String],
    description: String,
    bridges: Map[String, Any]
  ): GraalScriptPool = {
    val shimDir =
      if (jsShimFoldersToCopy.nonEmpty) Some(prepareNodeJsResourceDir(jsShimFoldersToCopy))
      else None

    factory(
      GraalPoolConfig(
        allowIO = IOAccess.ALL,
        allowHostAccess = HostAccess.EXPLICIT,
        description = Some(description)
      ),
      extendEngine = Some { (eb: Engine#Builder) =>
        eb.option("js.commonjs-require", "true")

        shimDir.foreach(dir => eb.option("js.commonjs-require-cwd", dir.toString))
      },
      extendContextBuilder = Some { (ctxb: Context#Builder, index: Int) =>
        ctxb
          .allowPolyglotAccess(PolyglotAccess.ALL)
          .option("js.polyglot-builtin", "true")
          .option("js.ecmascript-version", "2022")
         // .option("js.v8-compat", "true") // Node/V8-like quirks
      },
      extendContext = Some { (ctx: Context) =>
        bridges.foreach { case (name, bridge) =>
          ctx.getPolyglotBindings.putMember(name, bridge)
        }
      }
    )
  }

  private def prepareNodeJsResourceDir(jsShimsToCopy: Seq[String]): java.nio.file.Path = {
    val dir = Files.createTempDirectory(tempShimFolder)
    val nm = dir.resolve("node_modules")
    Files.createDirectories(nm)

    def copy(packageName: String) = {
      val nmf = nm.resolve(packageName)
      Files.createDirectories(nmf)
      val js = nmf.resolve("index.js")

      val fullIndexPath = s"/$jsShimsResourcePath/$packageName/index.js"
      val resourceStream: InputStream = getClass.getResourceAsStream(fullIndexPath)
      if (resourceStream == null) {
        throw new IllegalStateException(s"Could not find resource at path: $fullIndexPath")
      }
      val content = scala.io.Source.fromInputStream(resourceStream).mkString

      Files.writeString(
        js,
        content
      )
    }

    jsShimsToCopy.foreach(copy)

    dir
  }
}
