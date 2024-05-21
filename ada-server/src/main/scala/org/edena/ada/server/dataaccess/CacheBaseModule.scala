package org.edena.ada.server.dataaccess

import net.codingwell.scalaguice.ScalaModule
import org.edena.ada.server.dataaccess.ignite.mongo.{CacheMongoCrudStoreFactory, CacheMongoCrudStoreFactoryImpl}
import org.edena.ada.server.dataaccess.ignite.{CacheCrudStoreFactory, CacheCrudStoreFactoryImpl, IgniteFactory, IgniteLifecycleBean}
import org.apache.ignite.Ignite
import org.apache.ignite.lifecycle.LifecycleBean

class CacheBaseModule extends ScalaModule {

  override def configure = {
    // Ignite Base

    bind[Ignite].toProvider(classOf[IgniteFactory]).asEagerSingleton

    bind[LifecycleBean].to(classOf[IgniteLifecycleBean]).asEagerSingleton()

    // Cache/Ignite w. Mongo

    bind[CacheCrudStoreFactory].to(classOf[CacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[CacheMongoCrudStoreFactory].to(classOf[CacheMongoCrudStoreFactoryImpl]).asEagerSingleton()
  }
}