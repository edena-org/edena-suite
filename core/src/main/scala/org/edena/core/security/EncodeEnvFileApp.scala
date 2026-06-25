package org.edena.core.security

import com.typesafe.config.ConfigFactory
import org.edena.core.util.writeStringAsStream

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import scala.io.Source

/**
 * CLI entry point for [[EncodeEnvFile]].
 *
 * Usage:
 *   sbt "core/runMain org.edena.core.security.EncodeEnvFileApp <env-file> [out-file]"
 *
 * Reads the master key from `edena.encryption-key` (env `EDENA_ENCRYPTION_KEY`) and ABORTS if it
 * is not set — there is no fallback key, so encoding would be impossible. Writes in place by
 * default; pass a second path to write elsewhere. Idempotent: re-running re-encodes nothing.
 */
object EncodeEnvFileApp extends App {

  if (args.isEmpty) {
    Console.err.println("Usage: EncodeEnvFileApp <env-file> [out-file]")
    sys.exit(2)
  }

  private val inPath = args(0)
  private val outPath = if (args.length > 1) args(1) else inPath

  private val inFile = new File(inPath)
  if (!inFile.isFile) {
    Console.err.println(s"Input file not found: $inPath")
    sys.exit(2)
  }

  private val config = ConfigFactory.load()
  if (!SymmetricCrypto.isKeyConfigured(config)) {
    Console.err.println(
      s"'${SymmetricCrypto.ConfigKey}' (env EDENA_ENCRYPTION_KEY) is not set — required to encode."
    )
    sys.exit(1)
  }

  private val crypto = SymmetricCrypto(config)
  private val content = {
    val src = Source.fromFile(inFile, "UTF-8")
    try src.mkString
    finally src.close()
  }

  private val (encoded, summary) = EncodeEnvFile.encode(content, crypto)

  // Write atomically (temp in the same dir, then replace) so a crash mid-write cannot clobber the
  // existing secret file with a partial one.
  private val outFile = new File(outPath)
  private val tmpFile = new File(outFile.getAbsoluteFile.getParentFile, s".${outFile.getName}.tmp")
  writeStringAsStream(encoded, tmpFile)
  Files.move(tmpFile.toPath, outFile.toPath, StandardCopyOption.REPLACE_EXISTING)

  println(
    s"Encoded '$inPath' -> '$outPath': ${summary.encrypted} encrypted, " +
      s"${summary.skippedNoEnc} skipped (${EncodeEnvFile.NoEncMarker}), " +
      s"${summary.alreadyEncrypted} already encrypted, ${summary.skippedEmpty} empty, " +
      s"${summary.skippedMalformed} unparseable."
  )
}
