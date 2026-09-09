package org.edena.core.security

import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EncodeEnvFileSpec extends AnyFlatSpec with Matchers {

  private val crypto = new SymmetricCrypto("env-file-test-key")

  private def valueOf(line: String): String =
    line.split("=", 2)(1).split("\\s+#", 2)(0).trim.stripPrefix("\"").stripSuffix("\"")

  "EncodeEnvFile" should "encrypt plain values and annotate them with the last 4 letters" in {
    val (out, summary) = EncodeEnvFile.encode("export FOO=password456", crypto)

    summary.encrypted shouldBe 1
    out should include(SymmetricCrypto.Prefix)
    out should include("# original (last 4 letters): d456")
    crypto.decrypt(valueOf(out)) shouldBe "password456"
  }

  it should "skip values whose comment contains the no_enc marker" in {
    val line = "export SAFE=keepme   # no_enc"
    val (out, summary) = EncodeEnvFile.encode(line, crypto)

    out shouldBe line
    summary.skippedNoEnc shouldBe 1
  }

  it should "be idempotent — leave already-encrypted values untouched" in {
    val encrypted = "export TOK=" + crypto.encrypt("token")
    val (out, summary) = EncodeEnvFile.encode(encrypted, crypto)

    out shouldBe encrypted
    summary.alreadyEncrypted shouldBe 1
  }

  it should "preserve quotes and round-trip a quoted value" in {
    val (out, summary) = EncodeEnvFile.encode("""export Q="my secret"""", crypto)

    summary.encrypted shouldBe 1
    out should include("=\"" + SymmetricCrypto.Prefix)
    crypto.decrypt(valueOf(out)) shouldBe "my secret"
  }

  it should "not touch a #-containing quoted value as if it were a comment" in {
    val (out, _) = EncodeEnvFile.encode("""export H="a # b"""", crypto)
    crypto.decrypt(valueOf(out)) shouldBe "a # b"
  }

  it should "pass non-export and blank lines through verbatim, preserving structure" in {
    val content =
      """# a header comment
        |
        |export FOO=secret
        |plain line""".stripMargin
    val (out, summary) = EncodeEnvFile.encode(content, crypto)

    val lines = out.split("\n", -1)
    lines(0) shouldBe "# a header comment"
    lines(1) shouldBe ""
    lines(2) should startWith("# original (last 4 letters):")
    lines(3) should startWith("export FOO=" + SymmetricCrypto.Prefix)
    lines(4) shouldBe "plain line"
    summary.encrypted shouldBe 1
  }

  it should "skip empty values" in {
    val (out, summary) = EncodeEnvFile.encode("export EMPTY=", crypto)
    out shouldBe "export EMPTY="
    summary.skippedEmpty shouldBe 1
  }

  it should "never write the full secret back for short values (≤ 4 chars)" in {
    val (out, summary) = EncodeEnvFile.encode("export PIN=1234", crypto)
    summary.encrypted shouldBe 1
    // The comment (everything from '#') must not echo the plaintext; the ciphertext before it may
    // coincidentally contain any base64 chars, so only the comment is checked.
    val comment = out.substring(out.indexOf('#'))
    comment should not include "1234"
    comment should include("too short to hint")
    crypto.decrypt(valueOf(out)) shouldBe "1234"
  }

  it should "only opt out on a whole-word no_enc, not a coincidental substring" in {
    // 'GENO_ENColumn' lowercases to 'geno_encolumn' which contains 'no_enc' as a substring.
    val (out, summary) = EncodeEnvFile.encode("export TOKEN=secretval   # GENO_ENColumn note", crypto)
    summary.encrypted shouldBe 1
    summary.skippedNoEnc shouldBe 0
    out should include(SymmetricCrypto.Prefix)
  }

  it should "pass a line through untouched when there is junk after a closing quote" in {
    val line = """export X="abc" trailing"""
    val (out, summary) = EncodeEnvFile.encode(line, crypto)
    out shouldBe line
    summary.skippedMalformed shouldBe 1
  }

  it should "preserve CRLF line endings" in {
    val (out, summary) = EncodeEnvFile.encode("export FOO=secret\r\nplain\r\n", crypto)
    val lines = out.split("\n", -1)
    lines(0) should endWith("\r")
    lines(0) should startWith("# original (last 4 letters):")
    lines(1) should endWith("\r")
    lines(1) should startWith("export FOO=" + SymmetricCrypto.Prefix)
    lines(2) shouldBe "plain\r"
    summary.encrypted shouldBe 1
  }
}
