package org.edena.core.security

import com.typesafe.config.{Config, ConfigList, ConfigValue, ConfigValueFactory, ConfigValueType}
import org.edena.core.util.LoggingSupport

import scala.jdk.CollectionConverters._

/**
 * Transparently decrypts `enc:v1:`-prefixed string values in a loaded [[Config]].
 *
 * This is the single chokepoint for config decryption: it is applied right after the config is
 * loaded (in the `Config` provider / Play application loader), so every downstream read — direct
 * `config.getString(...)`, the `ConfigImplicits` helpers, and Play's injected `Configuration` —
 * sees plaintext without any read-site changes.
 *
 * Typesafe Config has already resolved all `${?ENV}` substitutions by the time `decrypt` runs, so
 * the values walked here are final. Encrypted strings are decrypted both as plain scalar values
 * AND as elements nested inside lists (e.g. `keys = ["enc:v1:...", "plain"]`); everything else is
 * returned untouched. A config with no encrypted values is returned as-is — no crypto is
 * initialized and nothing is logged.
 *
 * Expects a resolved config (the normal post-`load()` state); `entrySet()` throws on an unresolved
 * one. Fails closed: if any encrypted value is present but the master key is unset, or a value
 * cannot be decrypted, it throws (naming the path, never the value) rather than passing ciphertext
 * through.
 */
object ConfigDecryptor extends LoggingSupport {

  def decrypt(config: Config): Config = {
    // Paths whose value contains an `enc:v1:` string — directly, or as a (possibly nested) list element.
    val encryptedPaths = config.entrySet().asScala.toSeq.collect {
      case entry if containsEncrypted(entry.getValue) => entry.getKey
    }

    if (encryptedPaths.isEmpty) config
    else {
      if (!SymmetricCrypto.isKeyConfigured(config))
        throw new IllegalStateException(
          s"${encryptedPaths.size} encrypted config value(s) found but '${SymmetricCrypto.ConfigKey}' " +
            "(env EDENA_ENCRYPTION_KEY) is not set — cannot decrypt."
        )

      val crypto = SymmetricCrypto(config)

      encryptedPaths.foldLeft(config) { (acc, path) =>
        acc.withValue(path, decryptValue(acc.getValue(path), crypto, path))
      }
    }
  }

  /** True iff `value` is, or contains (within lists), an `enc:v1:`-prefixed string. */
  private def containsEncrypted(value: ConfigValue): Boolean =
    value.valueType match {
      case ConfigValueType.STRING =>
        value.unwrapped.asInstanceOf[String].startsWith(SymmetricCrypto.Prefix)
      case ConfigValueType.LIST =>
        value.asInstanceOf[ConfigList].asScala.exists(containsEncrypted)
      case _ => false
    }

  /** Return `value` with every encrypted string (incl. those nested in lists) decrypted. */
  private def decryptValue(value: ConfigValue, crypto: SymmetricCrypto, path: String): ConfigValue =
    value.valueType match {
      case ConfigValueType.STRING =>
        val s = value.unwrapped.asInstanceOf[String]
        if (s.startsWith(SymmetricCrypto.Prefix))
          ConfigValueFactory.fromAnyRef(decryptOne(crypto, s, path))
        else value
      case ConfigValueType.LIST =>
        val decrypted = value.asInstanceOf[ConfigList].asScala.map(decryptValue(_, crypto, path))
        ConfigValueFactory.fromIterable(decrypted.map(_.unwrapped).asJava)
      case _ => value
    }

  private def decryptOne(crypto: SymmetricCrypto, encrypted: String, path: String): String =
    try crypto.decrypt(encrypted)
    catch {
      case e: Exception =>
        // A corrupt/tampered secret must fail loudly, not silently degrade. Don't log the value
        // or the underlying message (may leak ciphertext detail) — only the path.
        throw new RuntimeException(
          s"Failed to decrypt config value at '$path'. The value may be corrupt or " +
            "encrypted with a different EDENA_ENCRYPTION_KEY.",
          e
        )
    }
}
