package org.edena.core.akka.guice

import com.google.inject.Provider
import com.typesafe.config.{Config, ConfigFactory}
import net.codingwell.scalaguice.ScalaModule
import org.edena.core.akka.guice.ConfigModule.ConfigProvider
import org.edena.core.security.ConfigDecryptor

object ConfigModule {

  /**
   * @param decryptConfigValues when true, `enc:v1:`-prefixed values are transparently decrypted
   *   via [[ConfigDecryptor]]; when false (default) the config is returned as loaded.
   */
  class ConfigProvider(decryptConfigValues: Boolean = false) extends Provider[Config] {
    override def get(): Config = {
      val config = ConfigFactory.load()
      if (decryptConfigValues) ConfigDecryptor.decrypt(config) else config
    }
  }
}

/**
 * Binds the application configuration to the [[Config]] interface.
 *
 * The config is bound as an eager singleton so that errors in the config are detected
 * as early as possible.
 *
 * @param decryptConfigValues opt-in flag (default `false`) enabling transparent decryption of
 *   `enc:v1:`-prefixed config values via [[ConfigDecryptor]]. Off by default so the generic
 *   non-Play/Akka path pays nothing unless it actually stores encrypted config; ada-web enables
 *   decryption via its application loader instead. Env-var decryption is separate — see
 *   [[org.edena.core.security.EnvDecryptor]].
 */
class ConfigModule(decryptConfigValues: Boolean = false) extends ScalaModule {

  override def configure(): Unit = {
    bind[Config].toProvider(new ConfigProvider(decryptConfigValues)).asEagerSingleton()
  }
}
