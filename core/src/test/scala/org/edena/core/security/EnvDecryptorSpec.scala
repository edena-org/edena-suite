package org.edena.core.security

import org.scalatest._

class EnvDecryptorSpec extends FlatSpec with Matchers {

  private val crypto = new SymmetricCrypto("env-decryptor-test-key")

  "EnvDecryptor.decryptedEntries" should "decrypt only enc:v1: values, leaving plaintext out" in {
    val enc = crypto.encrypt("topsecret")
    val env = Map("API_KEY" -> enc, "HOME" -> "/home/peter", "EMPTY" -> "")

    val result = EnvDecryptor.decryptedEntries(env, crypto)

    result shouldBe Map("API_KEY" -> "topsecret")
  }

  it should "decrypt every enc:v1: entry (no allow-list — all keys are scanned)" in {
    val env = Map("A" -> crypto.encrypt("a"), "B" -> crypto.encrypt("b"), "C" -> "plain")

    EnvDecryptor.decryptedEntries(env, crypto) shouldBe Map("A" -> "a", "B" -> "b")
  }

  it should "fail loudly (naming the key) on a value that cannot be decrypted" in {
    val env = Map("BAD" -> (SymmetricCrypto.Prefix + "not-valid-base64-or-cipher"))
    val ex = the[RuntimeException] thrownBy EnvDecryptor.decryptedEntries(env, crypto)
    ex.getMessage should include("BAD")
  }

  // Actually mutates the JVM's process environment — needs
  // `--add-opens java.base/java.util=ALL-UNNAMED` (configured for core/Test in build.sbt).
  "EnvDecryptor.decryptInPlace" should "rewrite an encrypted env var so System.getenv returns plaintext" in {
    val key = "EDENA_ENV_DECRYPT_TEST"
    val backing = modifiableEnv()
    backing.put(key, crypto.encrypt("rotated-secret"))
    try {
      System.getenv(key) should startWith(SymmetricCrypto.Prefix)

      // All env vars are scanned; assert on our seeded one (the JVM's other vars are plaintext).
      EnvDecryptor.decryptInPlace(crypto).rewritten should contain(key)

      System.getenv(key) shouldBe "rotated-secret"
    } finally backing.remove(key)
  }

  // Mirror of EnvDecryptor's reflection, used here only to seed/clean up the test variable.
  private def modifiableEnv(): java.util.Map[String, String] = {
    val env = System.getenv()
    val field = env.getClass.getDeclaredField("m")
    field.setAccessible(true)
    field.get(env).asInstanceOf[java.util.Map[String, String]]
  }
}
