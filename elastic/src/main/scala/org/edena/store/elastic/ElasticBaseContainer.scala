package org.edena.store.elastic

import com.sksamuel.elastic4s.ElasticClient
import net.codingwell.scalaguice.ScalaModule
import org.edena.core.akka.guice.{AkkaModule, ConfigModule, GuiceContainer}

trait ElasticBaseContainer extends GuiceContainer {

  override protected def modules = Seq(
    new ConfigModule(),
    new AkkaModule(),
    new ElasticBaseModule()
  )
}

class ElasticBaseModule extends ScalaModule {
  override def configure = {
    bind[ElasticClient].toProvider(new BasicElasticClientProvider).asEagerSingleton
  }
}
