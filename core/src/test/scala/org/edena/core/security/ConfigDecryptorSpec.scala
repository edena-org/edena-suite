package org.edena.core.security

import com.typesafe.config.ConfigFactory
import org.scalatest._

import scala.jdk.CollectionConverters._

class ConfigDecryptorSpec extends FlatSpec with Matchers {

  private val masterKey = "config-decryptor-test-key"
  private val crypto = new SymmetricCrypto(masterKey)

  "ConfigDecryptor" should "decrypt enc:v1: values while leaving plaintext and the key untouched" in {
    val encryptedSecret = crypto.encrypt("s3cr3t-password")
    val config = ConfigFactory.parseString(
      s"""
         |${SymmetricCrypto.ConfigKey} = "$masterKey"
         |mongodb.password = "$encryptedSecret"
         |mongodb.host = "localhost:27017"
       """.stripMargin
    )

    val decrypted = ConfigDecryptor.decrypt(config)

    decrypted.getString("mongodb.password") shouldBe "s3cr3t-password"
    decrypted.getString("mongodb.host") shouldBe "localhost:27017"
    decrypted.getString(SymmetricCrypto.ConfigKey) shouldBe masterKey
  }

  it should "decrypt encrypted string elements nested inside a list" in {
    val a = crypto.encrypt("key-A")
    val b = crypto.encrypt("key-B")
    val config = ConfigFactory.parseString(
      s"""${SymmetricCrypto.ConfigKey} = "$masterKey"
         |apiKeys = ["$a", "plain", "$b"]""".stripMargin
    )

    val decrypted = ConfigDecryptor.decrypt(config)

    decrypted.getStringList("apiKeys").asScala.toSeq shouldBe Seq("key-A", "plain", "key-B")
  }

  it should "return the same config unchanged when no encrypted values are present" in {
    val config = ConfigFactory.parseString("a.b = \"plain\"\na.c = 42")
    ConfigDecryptor.decrypt(config) shouldBe config
  }

  it should "fail loudly when an encrypted value cannot be decrypted" in {
    val config = ConfigFactory.parseString(
      s"""
         |${SymmetricCrypto.ConfigKey} = "wrong-key"
         |secret = "${crypto.encrypt("value")}"
       """.stripMargin
    )
    a[RuntimeException] should be thrownBy ConfigDecryptor.decrypt(config)
  }
}
