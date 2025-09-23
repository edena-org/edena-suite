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

final class GraalVmModule extends ScalaModule {

  override def configure(): Unit = {

    // factories
    bind[GraalPoolFactory]
      .annotatedWith(classOf[JsPoolFactory]) // qualifier for the *factory*
      .to(classOf[GraalJsPoolFactoryImpl])
      .asEagerSingleton()

    bind[GraalPoolFactory]
      .annotatedWith(classOf[PyPoolFactory]) // qualifier for the *factory*
      .to(classOf[GraalPyPoolFactoryImpl])
      .asEagerSingleton()

    // Eagerly touch defaults
    bind(classOf[WarmUpDefaults]).asEagerSingleton()
  }

  // Default pools (always present)
  @Provides @Singleton @JsDefault
  def provideDefaultJsPool(
    @JsPoolFactory factory: GraalPoolFactory
  ): GraalScriptPool =
    factory(
      GraalPoolConfig(
        // safest defaults for “strings & stuff only”
        allowHostAccess = HostAccess.NONE,
        description = Some("default - basic user")
      )
    )

  @Provides @Singleton @PyDefault
  def provideDefaultPyPool(@PyPoolFactory factory: GraalPoolFactory): GraalScriptPool =
    factory(
      GraalPoolConfig(
        allowHostAccess = HostAccess.NONE,
        description = Some("default - basic user")
      )
    )
}

// Touch the pools so they materialize eagerly
final class WarmUpDefaults @Inject() (
  @JsDefault js: Provider[GraalScriptPool],
  @PyDefault py: Provider[GraalScriptPool]
) { js.get(); py.get() }