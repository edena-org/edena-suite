package org.edena.core.akka.guice

import akka.actor.ActorSystem
import AkkaModule.{ActorSystemProvider, ExecutionContextProvider, MaterializerProvider}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import com.google.inject.{AbstractModule, Injector, Provider}
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule

import javax.inject.Inject
import scala.concurrent.ExecutionContext

object AkkaModule {

  private val name = "main-actor-system"

  class ActorSystemProvider @Inject() (val config: Config, val injector: Injector) extends Provider[ActorSystem] {
    override def get() = {
      val system = ActorSystem(name, config)
      GuiceAkkaExtension(system).initialize(injector)
      system
    }
  }

  class MaterializerProvider @Inject()(system: ActorSystem) extends Provider[Materializer] {

    override def get: Materializer = {
      val settings = ActorMaterializerSettings.create(system)
      ActorMaterializer.create(settings, system, name)
    }
  }

  class ExecutionContextProvider @Inject()(materializer: Materializer) extends Provider[ExecutionContext] {
    override def get: ExecutionContext = materializer.executionContext
  }

  class BlockingDispatchedExecutionContextProvider @Inject()(system: ActorSystem) extends Provider[ExecutionContext] {
    override def get: ExecutionContext = system.dispatchers.lookup("blocking-dispatcher")
  }
}

/**
 * A module providing an Akka ActorSystem.
 */
class AkkaModule(includeExecutionContext: Boolean = true) extends AbstractModule with ScalaModule {

  override def configure() {
    bind[ActorSystem].toProvider[ActorSystemProvider].asEagerSingleton()
    bind[Materializer].toProvider[MaterializerProvider].asEagerSingleton()

    if (includeExecutionContext) {
      bind[ExecutionContext].toProvider[ExecutionContextProvider].asEagerSingleton()
    }
  }
}