package org.edena.core.akka.guice

import com.google.inject.Provider
import com.typesafe.config.{Config, ConfigFactory}
import net.codingwell.scalaguice.ScalaModule
import org.edena.core.akka.guice.ConfigModule.ConfigProvider

object ConfigModule {
  class ConfigProvider extends Provider[Config] {
    override def get() = ConfigFactory.load()
  }
}

/**
 * Binds the application configuration to the [[Config]] interface.
 *
 * The config is bound as an eager singleton so that errors in the config are detected
 * as early as possible.
 */
class ConfigModule extends ScalaModule {

  override def configure() {
    bind[Config].toProvider[ConfigProvider].asEagerSingleton()
  }
}
