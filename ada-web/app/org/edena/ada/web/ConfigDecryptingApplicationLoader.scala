package org.edena.ada.web

import org.edena.core.security.{ConfigDecryptor, EnvDecryptor}
import play.api.ApplicationLoader.Context
import play.api.Configuration
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}

/**
 * Play application loader that, before the Guice application is built:
 *   1. decrypts `enc:v1:`-prefixed **config** values (via [[ConfigDecryptor]]), making them
 *      transparent to every consumer of the injected `Config` / [[Configuration]], and
 *   2. decrypts `enc:v1:`-prefixed **environment variables** in place (via [[EnvDecryptor]]), so
 *      libraries that read secrets straight from `System.getenv(...)` also see plaintext.
 *
 * Running here — before the injector exists — guarantees decryption happens ahead of any service
 * or eager singleton that might read a secret. Wired in via `play.application.loader` in
 * `conf/application.conf`.
 */
class ConfigDecryptingApplicationLoader extends GuiceApplicationLoader {

  override protected def builder(context: Context): GuiceApplicationBuilder = {
    val decryptedConfig = ConfigDecryptor.decrypt(context.initialConfiguration.underlying)

    // Rewrite encrypted env vars in place (no-op unless some carry the enc:v1: prefix).
    EnvDecryptor.decryptInPlace(decryptedConfig)

    initialBuilder
      .in(context.environment)
      .loadConfig(Configuration(decryptedConfig))
      .overrides(overrides(context): _*)
  }
}
