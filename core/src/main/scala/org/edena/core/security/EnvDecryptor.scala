package org.edena.core.security

import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits._
import org.edena.core.util.LoggingSupport

import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Decrypts `enc:v1:`-prefixed **environment variables** in place, so that third-party libraries
 * which read secrets straight from `System.getenv(...)` (e.g. the openai-scala-client
 * `AnthropicServiceFactory` / `EnvHelper`) transparently receive plaintext — they never see the
 * Typesafe `Config`, so [[ConfigDecryptor]] cannot reach them.
 *
 * `System.getenv()` returns an unmodifiable map, so the only way to rewrite it is to reflectively
 * reach the JVM's backing process-environment map and `put` the decrypted value. This REQUIRES the
 * JVM flag `--add-opens java.base/java.util=ALL-UNNAMED` (and on some setups
 * `--add-opens java.base/java.lang=ALL-UNNAMED`); without it [[decryptInPlace]] throws a clear,
 * actionable error — but only when there is actually an encrypted variable to rewrite, so apps
 * that use no encrypted env vars never trigger the reflection and need no flag.
 *
 * Idempotent: after a pass the values are plaintext (no `enc:v1:` prefix), so a second pass is a
 * no-op. Run as early as possible — before any consumer reads the variable (see the ada-web
 * application loader and [[EnvDecryptModule]]).
 */
object EnvDecryptor extends LoggingSupport {

  /** Names of the variables that were rewritten in the last pass. */
  case class Result(rewritten: Seq[String])

  /**
   * Convenience entry point reading everything from `config`: the master key
   * (`edena.encryption-key`) and an optional allow-list `edena.decrypt-env-keys` (a string list;
   * when set, only those variables are considered — otherwise every env var is scanned for the
   * prefix). Skips building the crypto entirely when nothing needs decrypting.
   */
  def decryptInPlace(config: Config): Result = {
    val keys = config.optionalStringSeq("edena.decrypt-env-keys").map(_.toSet)
    if (!hasEncryptedEnv(keys)) Result(Nil)
    else decryptInPlace(SymmetricCrypto(config), keys)
  }

  /**
   * Decrypt encrypted env vars in place using `crypto`. `keys`, when given, restricts the set of
   * variable names considered; otherwise all env vars are scanned. Only values carrying the
   * `enc:v1:` prefix are rewritten.
   */
  def decryptInPlace(crypto: SymmetricCrypto, keys: Option[Set[String]] = None): Result = {
    val plaintexts = decryptedEntries(sysEnv, crypto, keys)
    if (plaintexts.isEmpty) Result(Nil)
    else {
      val backingMaps = modifiableEnvMaps() // throws an actionable error if reflection is blocked
      plaintexts.foreach { case (k, v) => backingMaps.foreach(_.put(k, v)) }
      val names = plaintexts.keys.toSeq.sorted
      // Log only the names — never the decrypted values.
      logger.info(s"Decrypted ${names.size} encrypted environment variable(s): ${names.mkString(", ")}")
      Result(names)
    }
  }

  /**
   * Pure: compute `key -> plaintext` for the env entries that need decrypting, without mutating
   * anything. Exposed for testing. A bad value fails loudly (naming the key, never the value).
   */
  def decryptedEntries(
    env: Map[String, String],
    crypto: SymmetricCrypto,
    keys: Option[Set[String]] = None
  ): Map[String, String] =
    candidates(env, keys).collect {
      case (k, v) if isEncrypted(v) =>
        k -> {
          try crypto.decrypt(v)
          catch {
            case e: Exception =>
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

  private def isEncrypted(v: String): Boolean = v != null && v.startsWith(SymmetricCrypto.Prefix)

  private def candidates(env: Map[String, String], keys: Option[Set[String]]): Map[String, String] =
    keys.map(ks => env.view.filterKeys(ks).toMap).getOrElse(env)

  private def hasEncryptedEnv(keys: Option[Set[String]]): Boolean =
    candidates(sysEnv, keys).valuesIterator.exists(isEncrypted)

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
