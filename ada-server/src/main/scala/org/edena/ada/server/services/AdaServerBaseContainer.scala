package org.edena.ada.server.services

import org.edena.ada.server.dataaccess.NoCacheRepoModule
import org.edena.core.akka.guice.{AkkaModule, ConfigModule, GuiceContainer}
import org.edena.store.elastic.json.ElasticJsonExtModule
import org.edena.store.mongo.MongoJsonExtModule

trait AdaServerBaseContainer extends GuiceContainer {

  override protected def modules = Seq(
    new ConfigModule(),
    new AkkaModule(),
    new MongoJsonExtModule(),   // contains also ReactiveMongoApi
    new ElasticJsonExtModule(), // contains also ElasticClient
    new NoCacheRepoModule(),
    new BaseServiceModule()
  )
}