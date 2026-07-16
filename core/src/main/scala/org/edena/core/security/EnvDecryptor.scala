package org.edena.core.security

import com.typesafe.config.Config
import org.edena.core.util.LoggingSupport

import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Decrypts `enc:v1:`-prefixed **environment variables** in place, so that third-party
 * libraries which read secrets straight from `System.getenv(...)` (e.g. the
 * openai-scala-client `AnthropicServiceFactory` / `EnvHelper`) transparently receive plaintext
 * — they never see the Typesafe `Config`, so [[ConfigDecryptor]] cannot reach them.
 *
 * `System.getenv()` returns an unmodifiable map, so the only way to rewrite it is to
 * reflectively reach the JVM's backing process-environment map and `put` the decrypted value.
 * This REQUIRES the JVM flag `--add-opens java.base/java.util=ALL-UNNAMED` (and on some setups
 * `--add-opens java.base/java.lang=ALL-UNNAMED`); without it [[decryptInPlace]] throws a
 * clear, actionable error — but only when there is actually an encrypted variable to rewrite,
 * so apps that use no encrypted env vars never trigger the reflection and need no flag.
 *
 * Idempotent: after a pass the values are plaintext (no `enc:v1:` prefix), so a second pass is
 * a no-op. Run as early as possible — before any consumer reads the variable (see the ada-web
 * application loader and [[EnvDecryptModule]]).
 */
object EnvDecryptor extends LoggingSupport {

  /** Names of the variables that were rewritten in the last pass. */
  case class Result(rewritten: Seq[String])

  /** Removed allow-list option — kept only to warn deployments that still set it. */
  private val deprecatedKeysPath = "edena.decrypt-env-keys"

  /**
   * Convenience entry point reading the master key from `config` (`edena.encryption-key`).
   * EVERY environment variable is scanned for the `enc:v1:` prefix. Skips building the crypto
   * entirely when nothing needs decrypting.
   */
  def decryptInPlace(config: Config): Result = {
    if (config.hasPath(deprecatedKeysPath))
      logger.warn(
        s"The config key '$deprecatedKeysPath' has been removed and is IGNORED: every environment " +
          s"variable with the '${SymmetricCrypto.Prefix}' prefix is now decrypted in place. " +
          "Remove the key from your configuration."
      )

    if (!hasEncryptedEnv) Result(Nil)
    else decryptInPlace(SymmetricCrypto(config))
  }

  /**
   * Decrypt encrypted env vars in place using `crypto`: every variable carrying the `enc:v1:`
   * prefix is rewritten.
   */
  def decryptInPlace(crypto: SymmetricCrypto): Result = {
    val plaintexts = decryptedEntries(sysEnv, crypto)

    if (plaintexts.isEmpty)
      Result(Nil)
    else {
      val backingMaps =
        modifiableEnvMaps() // throws an actionable error if reflection is blocked

      plaintexts.foreach { case (k, v) => backingMaps.foreach(_.put(k, v)) }
      val names = plaintexts.keys.toSeq.sorted
      // Log only the names — never the decrypted values.
      logger.info(
        s"Decrypted ${names.size} encrypted environment variable(s): ${names.mkString(", ")}"
      )
      Result(names)
    }
  }

  /**
   * Pure: compute `key -> plaintext` for the env entries that need decrypting, without
   * mutating anything. Exposed for testing. A bad value fails loudly (naming the key, never
   * the value).
   */
  def decryptedEntries(
    env: Map[String, String],
    crypto: SymmetricCrypto
  ): Map[String, String] =
    env.collect {
      case (k, v) if isEncrypted(v) =>
        k -> {
          try crypto.decrypt(v)
          catch {
            case e: Exception =>
              // Log before throwing (name only, never the value) so this is visible even if the
              // caller only surfaces a generic error upstream.
              logger.error(s"Failed to decrypt environment variable '$k'.")

              throw new RuntimeException(
                s"Failed to decrypt environment variable '$k'. It may be corrupt or encrypted " +
                  "with a different EDENA_ENCRYPTION_KEY.",
                e
              )
          }
        }
    }

  // -- helpers --

  private def sysEnv: Map[String, String] = System.getenv().asScala.toMap

  private def isEncrypted(v: String): Boolean =
    v != null && v.startsWith(SymmetricCrypto.Prefix)

  private def hasEncryptedEnv: Boolean = sysEnv.valuesIterator.exists(isEncrypted)

  /**
   * The JVM's modifiable backing map(s) for the process environment. On Unix there is one (the
   * `m` field of the unmodifiable wrapper returned by `getenv()`); on Windows there is also a
   * case-insensitive map. Throws with an actionable message if neither is reachable.
   */
  private def modifiableEnvMaps(): Seq[java.util.Map[String, String]] = {
    val maps = scala.collection.mutable.ListBuffer.empty[java.util.Map[String, String]]

    // Primary (Unix + the common case): the `m` field of Collections$UnmodifiableMap.
    Try {
      val env = System.getenv()
      val field = env.getClass.getDeclaredField("m")
      field.setAccessible(true)
      field.get(env).asInstanceOf[java.util.Map[String, String]]
    }.foreach(maps += _)

    // Windows: ProcessEnvironment.theCaseInsensitiveEnvironment (absent on Unix — ignored).
    Try {
      val pe = Class.forName("java.lang.ProcessEnvironment")
      val field = pe.getDeclaredField("theCaseInsensitiveEnvironment")
      field.setAccessible(true)
      field.get(null).asInstanceOf[java.util.Map[String, String]]
    }.foreach(maps += _)

    if (maps.isEmpty)
      throw new IllegalStateException(
        "Cannot rewrite environment variables for decryption: the JVM blocked reflective access " +
          "to the process environment. Add the JVM flag '--add-opens java.base/java.util=ALL-UNNAMED' " +
          "(and on some setups '--add-opens java.base/java.lang=ALL-UNNAMED') and restart."
      )

    maps.toSeq
  }
}
