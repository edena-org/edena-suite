package org.edena.core.security

import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule

import javax.inject.{Inject, Singleton}

/**
 * Guice module that decrypts `enc:v1:`-prefixed environment variables in place as early as
 * possible (an eager singleton run during injector creation), so libraries reading
 * `System.getenv(...)` directly see plaintext. See [[EnvDecryptor]].
 *
 * For the ada-web Play app this is handled even earlier by the application loader (before the
 * injector is built); this module is for non-Play / Akka apps that wire Guice via
 * [[org.edena.core.akka.guice.ConfigModule]].
 */
class EnvDecryptModule extends ScalaModule {
  override def configure(): Unit =
    bind[EnvDecryptInitializer].asEagerSingleton()
}

@Singleton
private[security] class EnvDecryptInitializer @Inject() (config: Config) {
  EnvDecryptor.decryptInPlace(config)
}
