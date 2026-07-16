package org.edena.core.security

import com.typesafe.config.ConfigFactory
import org.scalatest._

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

class SymmetricCryptoSpec extends FlatSpec with Matchers {

  private val crypto = new SymmetricCrypto("unit-test-master-key")

  "SymmetricCrypto" should "round-trip a secret" in {
    val secret = "AKIA-super-secret/value+with=symbols"
    crypto.decrypt(crypto.encrypt(secret)) shouldBe secret
  }

  it should "produce tagged ciphertext that differs from the plaintext" in {
    val encrypted = crypto.encrypt("hello")
    encrypted should not be "hello"
    encrypted should startWith(SymmetricCrypto.Prefix)
  }

  it should "use a fresh IV so the same plaintext encrypts to different ciphertexts" in {
    crypto.encrypt("same") should not be crypto.encrypt("same")
  }

  it should "be idempotent on already-encrypted input" in {
    val once = crypto.encrypt("secret")
    crypto.encrypt(once) shouldBe once
  }

  it should "return legacy non-prefixed values unchanged on decrypt" in {
    crypto.decrypt("plain-legacy-value") shouldBe "plain-legacy-value"
  }

  it should "leave empty input unchanged" in {
    crypto.encrypt("") shouldBe ""
  }

  it should "decrypt across instances sharing the same master key" in {
    val other = new SymmetricCrypto("unit-test-master-key")
    other.decrypt(crypto.encrypt("shared")) shouldBe "shared"
  }

  it should "fail to decrypt with a different master key" in {
    val other = new SymmetricCrypto("a-different-master-key")
    an[Exception] should be thrownBy other.decrypt(crypto.encrypt("secret"))
  }

  it should "fail loudly (auth tag) when the ciphertext is tampered with" in {
    val encrypted = crypto.encrypt("tamper-me")
    val raw = Base64.getDecoder.decode(encrypted.substring(SymmetricCrypto.Prefix.length))
    raw(raw.length - 1) = (raw(raw.length - 1) ^ 0x01).toByte // flip a bit in the tag
    val tampered = SymmetricCrypto.Prefix + Base64.getEncoder.encodeToString(raw)
    an[Exception] should be thrownBy crypto.decrypt(tampered)
  }

  it should "round-trip with a configured pepper" in {
    val peppered = new SymmetricCrypto("unit-test-master-key", Some("a-deployment-pepper"))
    peppered.decrypt(peppered.encrypt("secret")) shouldBe "secret"
  }

  it should "derive a different key when the pepper differs (same master key)" in {
    val a = new SymmetricCrypto("unit-test-master-key", Some("pepper-A"))
    val b = new SymmetricCrypto("unit-test-master-key", Some("pepper-B"))
    an[Exception] should be thrownBy b.decrypt(a.encrypt("secret"))
  }

  it should "treat 'no pepper' differently from a configured pepper" in {
    val noPepper = new SymmetricCrypto("unit-test-master-key")
    val peppered = new SymmetricCrypto("unit-test-master-key", Some("pepper"))
    an[Exception] should be thrownBy peppered.decrypt(noPepper.encrypt("secret"))
  }

  it should "ignore surrounding whitespace in the key and pepper (interop safety)" in {
    val a = new SymmetricCrypto("master", Some("pep"))
    val b = new SymmetricCrypto("  master\n", Some(" pep "))
    b.decrypt(a.encrypt("secret")) shouldBe "secret"
  }

  it should "treat a blank pepper the same as no pepper" in {
    val none = new SymmetricCrypto("master")
    val blank = new SymmetricCrypto("master", Some("   "))
    blank.decrypt(none.encrypt("secret")) shouldBe "secret"
  }

  it should "reject a too-short enc:v1: blob with a clear error" in {
    val tooShort = SymmetricCrypto.Prefix + Base64.getEncoder.encodeToString(Array.fill[Byte](8)(0))
    val ex = the[IllegalArgumentException] thrownBy crypto.decrypt(tooShort)
    ex.getMessage.toLowerCase should include("too short")
  }

  "SymmetricCrypto.apply" should "require the master key (no hardcoded fallback)" in {
    // Discovery off so the outcome doesn't depend on any ambient host fallback file being present.
    val config = ConfigFactory.parseString(s"${SymmetricCrypto.DiscoveryConfigKey} = false")
    val ex = the[IllegalStateException] thrownBy SymmetricCrypto(config)
    ex.getMessage should include(SymmetricCrypto.ConfigKey)
  }

  it should "read the master key and optional pepper from config" in {
    val config = ConfigFactory.parseString(
      s"""${SymmetricCrypto.ConfigKey} = "k"
         |${SymmetricCrypto.PepperConfigKey} = "p"""".stripMargin
    )
    val fromConfig = SymmetricCrypto(config)
    val direct = new SymmetricCrypto("k", Some("p"))
    fromConfig.decrypt(direct.encrypt("secret")) shouldBe "secret"
  }

  it should "read the master key from a file pointer (trimming the trailing newline)" in {
    val keyFile = Files.createTempFile("edena-key", ".txt")
    try {
      Files.write(keyFile, "file-master-key\n".getBytes(StandardCharsets.UTF_8))
      val config = ConfigFactory.parseString(
        s"""${SymmetricCrypto.DiscoveryConfigKey} = false
           |${SymmetricCrypto.KeyFileConfigKey} = "${keyFile.toAbsolutePath}"""".stripMargin
      )
      val fromFile = SymmetricCrypto(config)
      // The trailing newline is trimmed on read, so the derived key matches the direct one.
      val direct = new SymmetricCrypto("file-master-key")
      fromFile.decrypt(direct.encrypt("secret")) shouldBe "secret"
    } finally Files.deleteIfExists(keyFile)
  }

  it should "prefer the inline key over a file pointer" in {
    val keyFile = Files.createTempFile("edena-key", ".txt")
    try {
      Files.write(keyFile, "file-master-key".getBytes(StandardCharsets.UTF_8))
      val config = ConfigFactory.parseString(
        s"""${SymmetricCrypto.DiscoveryConfigKey} = false
           |${SymmetricCrypto.ConfigKey} = "inline-key"
           |${SymmetricCrypto.KeyFileConfigKey} = "${keyFile.toAbsolutePath}"""".stripMargin
      )
      val resolved = SymmetricCrypto(config)
      // The inline key wins: a value encrypted with it round-trips...
      resolved.decrypt(new SymmetricCrypto("inline-key").encrypt("x")) shouldBe "x"
      // ...while one encrypted with the (ignored) file key does not.
      a[Exception] should be thrownBy
        resolved.decrypt(new SymmetricCrypto("file-master-key").encrypt("x"))
    } finally Files.deleteIfExists(keyFile)
  }

  it should "read the optional pepper from a file pointer" in {
    val pepperFile = Files.createTempFile("edena-pepper", ".txt")
    try {
      Files.write(pepperFile, " file-pepper \n".getBytes(StandardCharsets.UTF_8))
      val config = ConfigFactory.parseString(
        s"""${SymmetricCrypto.DiscoveryConfigKey} = false
           |${SymmetricCrypto.ConfigKey} = "k"
           |${SymmetricCrypto.PepperFileConfigKey} = "${pepperFile.toAbsolutePath}"""".stripMargin
      )
      val fromFile = SymmetricCrypto(config)
      val direct = new SymmetricCrypto("k", Some("file-pepper"))
      fromFile.decrypt(direct.encrypt("secret")) shouldBe "secret"
    } finally Files.deleteIfExists(pepperFile)
  }
}
