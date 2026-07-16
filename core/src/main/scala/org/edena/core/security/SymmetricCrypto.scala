package org.edena.core.security

import com.typesafe.config.{Config, ConfigFactory}
import org.edena.core.util.LoggingSupport

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import javax.crypto.{Cipher, Mac}
import scala.jdk.CollectionConverters._
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
 * Both the master key and the pepper may alternatively be supplied from a file (so the secret never
 * appears in the process environment or an `env` listing): an explicit pointer
 * (`EDENA_ENCRYPTION_KEY_FILE` / `_PEPPER_FILE`), or one of several fixed fallback locations
 * (systemd credentials, a Docker/K8s secret mount, `/etc/edena/secrets`, or `~/.config/edena`).
 * The full precedence is documented on [[SymmetricCrypto.apply]]; file contents are trimmed and a
 * file readable beyond its owner is warned about (but still used). Keep the pepper file on a
 * DIFFERENT trust boundary than the key — co-located, it adds nothing.
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

object SymmetricCrypto extends LoggingSupport {

  /** Self-describing, versioned prefix marking an encrypted value (the `enc:<code>` tag). */
  val Prefix = "enc:v1:"

  /** Config path (env `EDENA_ENCRYPTION_KEY`) holding the master secret. */
  val ConfigKey = "edena.encryption-key"

  /** Config path (env `EDENA_ENCRYPTION_PEPPER`) holding the optional pepper / second factor. */
  val PepperConfigKey = "edena.encryption-pepper"

  /** Config path (env `EDENA_ENCRYPTION_KEY_FILE`) pointing at a file holding the master secret. */
  val KeyFileConfigKey = "edena.encryption-key-file"

  /** Config path (env `EDENA_ENCRYPTION_PEPPER_FILE`) pointing at a file holding the pepper. */
  val PepperFileConfigKey = "edena.encryption-pepper-file"

  /** Persistent bare-metal fallback dir for the MASTER KEY (`/etc/edena/secrets/encryption-key`). */
  val EtcSecretsDir = "/etc/edena/secrets"

  /**
   * Persistent bare-metal fallback dir for the PEPPER (`/var/lib/edena/secrets/encryption-pepper`).
   * Deliberately a DIFFERENT directory tree than [[EtcSecretsDir]] so the pepper can carry its own
   * ownership / mount — a genuine second trust boundary — rather than sitting next to the key. Both
   * are on-disk (unlike `/run/secrets`, which is tmpfs and lost on reboot).
   */
  val VarLibSecretsDir = "/var/lib/edena/secrets"

  /**
   * Config flag (default `true`): scan the ambient fixed fallback locations (systemd
   * `$CREDENTIALS_DIRECTORY`, `/run/secrets`, the per-secret persistent dir, `~/.config/edena`). Set
   * `false` to require secrets to come ONLY from the inline value or an explicit `*_FILE` pointer —
   * forbids a shared host file meant for another app from leaking in, and keeps tests independent of
   * host state.
   */
  val DiscoveryConfigKey = "edena.encryption-secret-discovery"

  private val Transformation = "AES/GCM/NoPadding"
  private val MacAlgorithm = "HmacSHA256"
  private val IvLength = 12 // 96-bit IV recommended for GCM
  private val TagBits = 128
  private val secureRandom = new java.security.SecureRandom()

  /** File permissions that mean "accessible beyond the owner" — warned about on a secret file. */
  private val LoosePerms: Set[PosixFilePermission] = Set(
    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE
  )

  /**
   * One resolvable secret (the master key or the pepper) and every place it can come from.
   *
   * @param label           human name for error messages ("master key" / "pepper").
   * @param shortName       file name under the systemd / `/etc` / XDG dirs (`encryption-key`).
   * @param dockerName      file name under `/run/secrets` (`edena-encryption-key`).
   * @param inlineConfigKey config path for the inline value (also the `${?ENV}` target).
   * @param fileConfigKey   config path for an explicit file pointer.
   * @param fileEnv         env var for an explicit file pointer (e.g. `EDENA_ENCRYPTION_KEY_FILE`).
   * @param secretsDir      persistent bare-metal fallback dir holding `shortName` (per-secret, so the
   *                        key and pepper live in separate directory trees).
   */
  private case class SecretSpec(
    label: String,
    shortName: String,
    dockerName: String,
    inlineConfigKey: String,
    fileConfigKey: String,
    fileEnv: String,
    secretsDir: String
  )

  private val KeySpec = SecretSpec(
    "master key", "encryption-key", "edena-encryption-key",
    ConfigKey, KeyFileConfigKey, "EDENA_ENCRYPTION_KEY_FILE", EtcSecretsDir
  )

  private val PepperSpec = SecretSpec(
    "pepper", "encryption-pepper", "edena-encryption-pepper",
    PepperConfigKey, PepperFileConfigKey, "EDENA_ENCRYPTION_PEPPER_FILE", VarLibSecretsDir
  )

  /** True iff the master key can be resolved from any source (inline value or a fallback file). */
  def isKeyConfigured(config: Config): Boolean = resolve(config, KeySpec).isDefined

  /** Human-readable list of where the master key may be supplied (for error messages). */
  def keySourcesHint: String = sourcesHint(KeySpec)

  /**
   * Build from a typesafe `Config`. The master key is REQUIRED, the pepper OPTIONAL; each is
   * resolved through the same fallback chain — the FIRST source that yields a non-empty value wins
   * (file contents are trimmed):
   *
   *   1. inline config value (`edena.encryption-key`, i.e. env `EDENA_ENCRYPTION_KEY` via `${?…}`);
   *   2. explicit file pointer (`edena.encryption-key-file` / env `EDENA_ENCRYPTION_KEY_FILE`);
   *   3. `$CREDENTIALS_DIRECTORY/encryption-key` (systemd `LoadCredential=`, tmpfs);
   *   4. `/run/secrets/edena-encryption-key` (Docker/K8s secret mount, tmpfs);
   *   5. `/etc/edena/secrets/encryption-key` (persistent bare-metal location);
   *   6. `~/.config/edena/encryption-key` (`$XDG_CONFIG_HOME`, for dev).
   *
   * The pepper uses the parallel `encryption-pepper` / `edena-encryption-pepper` names, EXCEPT its
   * persistent bare-metal dir is `/var/lib/edena/secrets` (not `/etc/edena/secrets`) — see
   * [[VarLibSecretsDir]] — so the two secrets never share a directory. A secret file readable beyond
   * its owner is warned about but still used. Throws when the master key is found in NO source —
   * there is no hardcoded fallback.
   */
  def apply(config: Config): SymmetricCrypto = {
    val key = resolve(config, KeySpec).getOrElse(
      throw new IllegalStateException(
        s"No encryption ${KeySpec.label} found, required to encrypt/decrypt $Prefix values. " +
          s"Supply it via $keySourcesHint."
      )
    )
    new SymmetricCrypto(key, resolve(config, PepperSpec))
  }

  /** Convenience: build from the default loaded config. */
  def default: SymmetricCrypto = apply(ConfigFactory.load())

  // -- secret resolution --

  /** First non-empty source for `spec`: inline config value, else the fallback files in order. */
  private def resolve(config: Config, spec: SecretSpec): Option[String] =
    optionalString(config, spec.inlineConfigKey)
      .orElse(candidatePaths(config, spec).iterator.flatMap(readSecretFile).nextOption())

  /** Fallback file paths for `spec`, highest precedence first (explicit pointer → … → dev XDG). */
  private def candidatePaths(config: Config, spec: SecretSpec): Seq[Path] = {
    // An explicit file pointer is ALWAYS honored; the ambient well-known locations are only scanned
    // when discovery is enabled (the default) — see [[DiscoveryConfigKey]].
    val explicit = optionalString(config, spec.fileConfigKey)
      .orElse(env(spec.fileEnv))
      .map(Paths.get(_))

    if (!discoveryEnabled(config)) explicit.toSeq
    else {
      val systemd = env("CREDENTIALS_DIRECTORY").map(d => Paths.get(d, spec.shortName))
      val dockerSecret = Some(Paths.get("/run/secrets", spec.dockerName))
      val persistent = Some(Paths.get(spec.secretsDir, spec.shortName))
      val xdg = env("XDG_CONFIG_HOME").map(Paths.get(_))
        .orElse(sys.props.get("user.home").filter(_.nonEmpty).map(h => Paths.get(h, ".config")))
        .map(_.resolve("edena").resolve(spec.shortName))
      Seq(explicit, systemd, dockerSecret, persistent, xdg).flatten
    }
  }

  private def discoveryEnabled(config: Config): Boolean =
    Try(config.getBoolean(DiscoveryConfigKey)).getOrElse(true)

  // Paths already logged about, so the warn / audit lines are not repeated when the chain is
  // resolved more than once per boot — the `EnvDecryptor` and `ConfigDecryptor` passes each build a
  // crypto, `ConfigDecryptor` additionally pre-checks via `isKeyConfigured`, and apps may reload.
  // JVM-lifetime, so it's exactly one warn + one audit line per file per process start.
  private val loggedFilePaths = ConcurrentHashMap.newKeySet[String]()

  /** Read a secret file: trimmed contents, or None if it is absent/unreadable/empty. */
  private def readSecretFile(path: Path): Option[String] =
    if (!Files.isRegularFile(path)) None
    else {
      val content =
        try new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim
        catch {
          case e: Exception =>
            logger.warn(s"Could not read encryption secret file '$path': ${e.getMessage}")
            ""
        }
      if (content.isEmpty) None
      else {
        // Loose-perms warning + audit trail (path only, never the value) — once per file per JVM.
        if (loggedFilePaths.add(path.toString)) {
          warnIfLoosePerms(path)
          logger.info(s"Loaded encryption secret from file '$path'.")
        }
        Some(content)
      }
    }

  /** Warn (but do not fail) when a secret file is group/other-accessible. POSIX-only; else skipped. */
  private def warnIfLoosePerms(path: Path): Unit =
    try {
      val loose = Files.getPosixFilePermissions(path).asScala.intersect(LoosePerms)
      if (loose.nonEmpty)
        logger.warn(
          s"Encryption secret file '$path' is accessible beyond its owner " +
            s"(${loose.toSeq.map(_.toString).sorted.mkString(", ")}) — tighten it with " +
            s"`chmod 600 '$path'` and ensure it is owned by the service account."
        )
    } catch {
      case _: UnsupportedOperationException => // non-POSIX filesystem (e.g. Windows) — skip the check
    }

  private def env(name: String): Option[String] =
    Option(System.getenv(name)).map(_.trim).filter(_.nonEmpty)

  private def optionalString(config: Config, path: String): Option[String] =
    Try(config.getString(path)).toOption.filter(_.trim.nonEmpty)

  private def sourcesHint(spec: SecretSpec): String = {
    val inlineEnv = spec.fileEnv.stripSuffix("_FILE")
    s"env $inlineEnv (config '${spec.inlineConfigKey}'); a file via env ${spec.fileEnv} " +
      s"(config '${spec.fileConfigKey}'); or a file at $$CREDENTIALS_DIRECTORY/${spec.shortName}, " +
      s"/run/secrets/${spec.dockerName}, ${spec.secretsDir}/${spec.shortName}, " +
      s"or ~/.config/edena/${spec.shortName}"
  }
}
