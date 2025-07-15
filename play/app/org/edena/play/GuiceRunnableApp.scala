package org.edena.play

import play.api.inject.guice.GuiceApplicationBuilder
import scala.jdk.CollectionConverters._

/**
  * A simple app backed by Guice IOC, which runs a given runnable.
  *
  * @author Peter Banda
  */
class GuiceRunnableApp[T <: Runnable](
  modules: Seq[String] = Nil)(
  implicit ev: Manifest[T]
) extends App {

  private val app = {
    val env = play.api.Environment.simple(mode = play.api.Mode.Dev)
    val config = play.api.Configuration.load(env)

    val availablePlayModules = config.getOptional[Seq[String]]("play.modules.enabled").getOrElse(Nil).toList

    // if modules are specified use them, otherwise load ALL available play modules
    val initModules = if (modules.nonEmpty) modules else availablePlayModules

    new GuiceApplicationBuilder().configure("play.modules.enabled" -> initModules).build()
  }

  app.injector.instanceOf[T].run
  app.stop()
}