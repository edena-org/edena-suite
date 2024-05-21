package org.edena.ada.server.dataaccess

import com.google.inject.Provider
import com.typesafe.config.{Config, ConfigFactory}
import net.codingwell.scalaguice.ScalaModule
import org.edena.ada.server.dataaccess.PlayConfigModule.{ConfigProvider, ConfigurationProvider}
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle}
import play.api.{Configuration, Environment}

import javax.inject.Inject

@Deprecated
object PlayConfigModule {
  private class ConfigProvider extends Provider[Config] {
    override def get = ConfigFactory.load()
  }

  private class ConfigurationProvider @Inject()(config: Config) extends Provider[Configuration] {
    override def get = new Configuration(config)
  }
}

@Deprecated
class PlayConfigModule extends ScalaModule {

  override def configure() {
    bind[Config].toProvider[ConfigProvider].asEagerSingleton()
    bind[Configuration].toProvider[ConfigurationProvider].asEagerSingleton()
    bind[Environment].toInstance(Environment.simple())
    bind[ApplicationLifecycle].toInstance(new DefaultApplicationLifecycle())
  }
}