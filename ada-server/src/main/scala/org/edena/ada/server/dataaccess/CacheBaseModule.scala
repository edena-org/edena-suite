package org.edena.ada.server.dataaccess

import net.codingwell.scalaguice.ScalaModule
import org.edena.ada.server.dataaccess.ignite.mongo.{CacheMongoCrudStoreFactory, CacheMongoCrudStoreFactoryImpl}
import org.edena.ada.server.dataaccess.ignite.{IgniteFactory, IgniteLifecycleBean}
import org.apache.ignite.Ignite
import org.apache.ignite.lifecycle.LifecycleBean
import org.edena.store.ignite.front.{CustomFromToCacheCrudStoreFactory, CustomFromToCacheCrudStoreFactoryImpl, IdentityCacheCrudStoreFactory, IdentityCacheCrudStoreFactoryImpl, ScalaJavaBinaryCacheCrudStoreFactory, ScalaJavaBinaryCacheCrudStoreFactoryImpl}

class CacheBaseModule extends ScalaModule {

  override def configure = {
    // Ignite Base

    bind[Ignite].toProvider(classOf[IgniteFactory]).asEagerSingleton

    bind[LifecycleBean].to(classOf[IgniteLifecycleBean]).asEagerSingleton()

    // Cache/Ignite w. Mongo

    bind[IdentityCacheCrudStoreFactory].to(classOf[IdentityCacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[CustomFromToCacheCrudStoreFactory].to(classOf[CustomFromToCacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[ScalaJavaBinaryCacheCrudStoreFactory].to(classOf[ScalaJavaBinaryCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[CacheMongoCrudStoreFactory].to(classOf[CacheMongoCrudStoreFactoryImpl]).asEagerSingleton()
  }
}