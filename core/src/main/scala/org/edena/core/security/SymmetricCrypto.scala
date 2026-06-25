package org.edena.core.security

import com.typesafe.config.{Config, ConfigFactory}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import javax.crypto.{Cipher, Mac}
import scala.util.Try

/**
 * Reversible symmetric encryption for config/env secrets that must be recovered in-app — not
 * one-way hashed. Uses AES/GCM/NoPadding with a 256-bit key derived from the configured master
 * secret. Each [[encrypt]] generates a fresh random 12-byte IV which is prepended to the
 * ciphertext+tag; the whole blob is Base64-encoded and tagged `enc:v1:` so encrypted values are
 * self-describing and the scheme can be versioned later.
 *
 * '''No secret material is hardcoded''' (this is an open-source project):
 *   - the master key comes from `edena.encryption-key` (env `EDENA_ENCRYPTION_KEY`) — required;
 *   - an OPTIONAL `pepper` (env `EDENA_ENCRYPTION_PEPPER`) is a deployment-held second factor.
 *     With a pepper the AES key is `HMAC-SHA256(pepper, masterKey)` (so the master key alone is
 *     not enough); without one it is simply `SHA-256(masterKey)` — no second factor, no constant.
 *
 * Changing the pepper (or setting/unsetting it) changes the derived key and therefore invalidates
 * all previously encrypted values.
 *
 * '''Use a high-entropy master key.''' The key derivation is a single hash (no PBKDF2/Argon2 work
 * factor or salt) — correct and fast for a random key, but weak for a guessable passphrase, since
 * the GCM tag gives an offline attacker a fast verify oracle. Generate one with e.g.
 * `openssl rand -base64 32`. Leading/trailing whitespace in the key and pepper is ignored.
 *
 * '''Interop.''' A value encrypted by one project decrypts in another iff the effective
 * (trimmed) master key AND pepper match — the format (`enc:v1:`) and derivation are otherwise
 * identical and stable across projects sharing this `core` library.
 *
 * Pass-throughs (returned unchanged, never encrypted/decrypted):
 *   - an already-`enc:v1:`-prefixed input to [[encrypt]] (idempotent — safe to re-run encoders),
 *   - a `null`/empty value, or a value without the `enc:v1:` prefix passed to [[decrypt]]
 *     (plaintext tolerance, so configs can mix encrypted and plain values).
 *
 * GCM authenticates: a tampered or truncated ciphertext throws
 * `javax.crypto.AEADBadTagException` on [[decrypt]] rather than returning garbage.
 *
 * @param masterKey the raw master secret.
 * @param pepper    optional deployment-held second factor; when present it is the HMAC key.
 */
class SymmetricCrypto(masterKey: String, pepper: Option[String] = None) {

  import SymmetricCrypto._

  private val keySpec: SecretKeySpec = {
    // Trim key material so a stray trailing newline/space (the classic `$(cat keyfile)` /
    // copy-paste mistake) cannot silently change the derived key across deployments. The pepper's
    // emptiness check and its byte material therefore use the SAME trimmed value — see [[apply]].
    val masterBytes = masterKey.trim.getBytes(StandardCharsets.UTF_8)
    // Derive a 256-bit AES key (both paths yield exactly 32 bytes). No constant is involved:
    //   - with a pepper: HMAC-SHA256(pepper, masterKey) — the pepper is a deployment-held second
    //     factor, so the master key alone cannot derive the AES key;
    //   - without a pepper: SHA-256(masterKey) — straight key derivation, no second factor.
    val digest = pepper.map(_.trim).filter(_.nonEmpty) match {
      case Some(p) =>
        val mac = Mac.getInstance(MacAlgorithm)
        mac.init(new SecretKeySpec(p.getBytes(StandardCharsets.UTF_8), MacAlgorithm))
        mac.doFinal(masterBytes)
      case None =>
        MessageDigest.getInstance("SHA-256").digest(masterBytes)
    }
    new SecretKeySpec(digest, "AES")
  }

  /** Encrypt a plaintext secret. Returns empty and already-encrypted values unchanged. */
  def encrypt(plain: String): String =
    if (plain == null || plain.isEmpty || plain.startsWith(Prefix)) plain
    else {
      val iv = new Array[Byte](IvLength)
      secureRandom.nextBytes(iv)

      val cipher = Cipher.getInstance(Transformation)
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TagBits, iv))
      val cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8))

      val combined = new Array[Byte](iv.length + cipherText.length)
      System.arraycopy(iv, 0, combined, 0, iv.length)
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length)

      Prefix + Base64.getEncoder.encodeToString(combined)
    }

  /**
   * Decrypt a value produced by [[encrypt]]. `null` and non-`enc:v1:` values are returned
   * unchanged. Fails closed on anything malformed: invalid Base64 throws
   * `IllegalArgumentException`; a too-short blob or a tampered/wrong-key ciphertext throws (GCM
   * authentication) — it never returns garbage.
   */
  def decrypt(stored: String): String =
    if (stored == null || !stored.startsWith(Prefix)) stored
    else {
      val combined = Base64.getDecoder.decode(stored.substring(Prefix.length))
      // Must hold at least the 12-byte IV plus the 16-byte GCM tag, else it cannot be ours.
      if (combined.length < IvLength + TagBits / 8)
        throw new IllegalArgumentException(
          s"Malformed $Prefix value: too short to contain the IV and authentication tag."
        )
      val iv = combined.take(IvLength)
      val cipherText = combined.drop(IvLength)

      val cipher = Cipher.getInstance(Transformation)
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TagBits, iv))
      new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
    }
}

object SymmetricCrypto {

  /** Self-describing, versioned prefix marking an encrypted value (the `enc:<code>` tag). */
  val Prefix = "enc:v1:"

  /** Config path (env `EDENA_ENCRYPTION_KEY`) holding the master secret. */
  val ConfigKey = "edena.encryption-key"

  /** Config path (env `EDENA_ENCRYPTION_PEPPER`) holding the optional pepper / second factor. */
  val PepperConfigKey = "edena.encryption-pepper"

  private val Transformation = "AES/GCM/NoPadding"
  private val MacAlgorithm = "HmacSHA256"
  private val IvLength = 12 // 96-bit IV recommended for GCM
  private val TagBits = 128
  private val secureRandom = new java.security.SecureRandom()

  /** True iff the master key is configured (env/config). */
  def isKeyConfigured(config: Config): Boolean =
    Try(config.getString(ConfigKey)).toOption.exists(_.trim.nonEmpty)

  private def optionalString(config: Config, path: String): Option[String] =
    Try(config.getString(path)).toOption.filter(_.trim.nonEmpty)

  /**
   * Build from a typesafe `Config`, reading the required master key `edena.encryption-key`
   * (env `EDENA_ENCRYPTION_KEY`) and the optional pepper `edena.encryption-pepper`
   * (env `EDENA_ENCRYPTION_PEPPER`). Throws when the master key is not set — there is no
   * hardcoded fallback.
   */
  def apply(config: Config): SymmetricCrypto = {
    val key = optionalString(config, ConfigKey).getOrElse(
      throw new IllegalStateException(
        s"'$ConfigKey' (env EDENA_ENCRYPTION_KEY) must be set to encrypt/decrypt $Prefix values."
      )
    )
    new SymmetricCrypto(key, optionalString(config, PepperConfigKey))
  }

  /** Convenience: build from the default loaded config. */
  def default: SymmetricCrypto = apply(ConfigFactory.load())
}
