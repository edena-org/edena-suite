package org.edena.scripting

import org.edena.core.akka.guice.{AkkaModule, ConfigModule, GuiceContainer}

trait GraalVmBaseContainer extends GuiceContainer {

  override protected def modules = Seq(
    new ConfigModule(),
    new AkkaModule(includeExecutionContext = true),
    new GraalVmModule()
  )
}