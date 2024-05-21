package org.edena.store.mongo

import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.{Key, TypeLiteral}
import com.google.inject.name.Names
import net.codingwell.scalaguice.ScalaModule
import org.edena.store.json.StoreTypes.JsonCrudStore

import scala.concurrent.Future

class MongoJsonModule extends ScalaModule {

  override def configure = {
    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[JsonCrudStore]{}, classOf[MongoJsonCrudStoreImpl])
      .build(Key.get(classOf[MongoJsonCrudStoreFactory], Names.named("MongoJsonCrudStoreFactory"))))
  }
}

class MongoJsonExtModule extends ScalaModule {

  override def configure = {
    bind[ReactiveMongoApi].toProvider(new ReactiveMongoProvider("default")).asEagerSingleton()

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[JsonCrudStore]{}, classOf[MongoJsonCrudStoreImpl])
      .build(Key.get(classOf[MongoJsonCrudStoreFactory], Names.named("MongoJsonCrudStoreFactory"))))
  }
}