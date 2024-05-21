package org.edena.ada.web.security

import be.objectify.deadbolt.scala.cache.HandlerCache
import be.objectify.deadbolt.scala.{DeadboltExecutionContextProvider, DeadboltModule, TemplateFailureListener}
import net.codingwell.scalaguice.ScalaModule
import play.api.{Configuration, Environment}

/**
  * Extension of Deadbolt module with some custom/Ada stuff
  */
class AdaDeadboltModule extends DeadboltModule {

  override def bindings(environment: Environment, configuration: Configuration) =
    super.bindings(environment, configuration) ++ Seq(
      bind[TemplateFailureListener].to[AdaTemplateFailureListener],
      bind[HandlerCache].to[CustomHandlerCacheImpl],
      bind[DeadboltExecutionContextProvider].to[AdaDeadboltExecutionContextProvider]
    )
}

//class AdaDeadboltModule extends ScalaModule {
//
//  override def configure(): Unit = {
//    bind[TemplateFailureListener].to[AdaTemplateFailureListener].asEagerSingleton()
//    bind[HandlerCache].to[CustomHandlerCacheImpl].asEagerSingleton()
//    bind[DeadboltExecutionContextProvider].to[AdaDeadboltExecutionContextProvider].asEagerSingleton()
//  }
//}

