package org.edena.store.mongo

import org.edena.core.akka.guice.{AkkaModule, ConfigModule, GuiceContainer}

trait MongoBaseContainer extends GuiceContainer {

  override protected def modules = Seq(
    new ConfigModule(),
    new AkkaModule(),
    new ReactiveMongoModule()
  )
}
