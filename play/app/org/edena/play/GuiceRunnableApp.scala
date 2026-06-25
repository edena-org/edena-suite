package org.edena.play

import org.edena.core.security.EnvDecryptor
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

    // Decrypt any `enc:v1:`-prefixed environment variables in place BEFORE the injector is built,
    // so every module / eager singleton / service it constructs sees plaintext. No-op (and no
    // reflection attempted) unless an encrypted env var is actually present. See EnvDecryptor.
    EnvDecryptor.decryptInPlace(config.underlying)

    val availablePlayModules = config.getOptional[Seq[String]]("play.modules.enabled").getOrElse(Nil).toList

    // if modules are specified use them, otherwise load ALL available play modules
    val initModules = if (modules.nonEmpty) modules else availablePlayModules

    new GuiceApplicationBuilder().configure("play.modules.enabled" -> initModules).build()
  }

  app.injector.instanceOf[T].run
  app.stop()
}