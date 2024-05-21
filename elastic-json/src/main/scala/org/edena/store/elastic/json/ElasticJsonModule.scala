package org.edena.store.elastic.json

import com.google.inject.{Key, TypeLiteral}
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.name.Names
import com.sksamuel.elastic4s.ElasticClient
import net.codingwell.scalaguice.ScalaModule
import org.edena.store.elastic.BasicElasticClientProvider
import StoreTypes.ElasticJsonCrudStore

final class ElasticJsonModule extends ScalaModule {

  override def configure = {
    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[ElasticJsonCrudStore]{}, classOf[ElasticJsonCrudStoreImpl])
      .build(Key.get(classOf[ElasticJsonCrudStoreFactory], Names.named("ElasticJsonCrudStoreFactory"))))
  }
}

final class ElasticJsonExtModule extends ScalaModule {

  override def configure = {
    bind[ElasticClient].toProvider(new BasicElasticClientProvider).asEagerSingleton

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[ElasticJsonCrudStore]{}, classOf[ElasticJsonCrudStoreImpl])
      .build(Key.get(classOf[ElasticJsonCrudStoreFactory], Names.named("ElasticJsonCrudStoreFactory"))))
  }
}
