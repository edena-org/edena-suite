package org.edena.ada.server.runnables.core

import com.typesafe.config.Config
import org.edena.core.runnables.InputRunnableExt
import org.edena.core.security.{SymmetricCrypto, EncodeEnvFile => EnvFileEncoder}
import org.edena.core.util.{LoggingSupport, writeStringAsStream}

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import javax.inject.Inject
import scala.io.Source

/**
 * Ada runnable wrapper around [[org.edena.core.security.EncodeEnvFile]]: encrypts the values
 * of an `export NAME=value` env file in place, leaving `# no_enc`-marked / empty /
 * already-encrypted values untouched. The master key is read from the app config
 * (`edena.encryption-key` / env `EDENA_ENCRYPTION_KEY`); it aborts with a clear error if
 * unset. Writes atomically and idempotently. See also the CLI
 * [[org.edena.core.security.EncodeEnvFileApp]].
 */
class EncodeEnvFile @Inject() (config: Config)
    extends InputRunnableExt[EncodeEnvFileSpec]
    with LoggingSupport {

  override def run(input: EncodeEnvFileSpec): Unit = {
    val inFile = new File(input.filePath)
    require(inFile.isFile, s"Env file not found: ${input.filePath}")

    // Throws a clear error if `edena.encryption-key` is not set — there is no fallback key.
    val crypto = SymmetricCrypto(config)

    val content = {
      val src = Source.fromFile(inFile, "UTF-8")
      try src.mkString
      finally src.close()
    }

    val (encoded, summary) = EnvFileEncoder.encode(content, crypto)

    // Write atomically (temp in the same dir, then replace) so a crash mid-write cannot clobber
    // the existing secret file with a partial one.
    val tmpFile = new File(inFile.getAbsoluteFile.getParentFile, s".${inFile.getName}.tmp")
    writeStringAsStream(encoded, tmpFile)
    Files.move(tmpFile.toPath, inFile.toPath, StandardCopyOption.REPLACE_EXISTING)

    logger.info(
      s"Encoded '${input.filePath}': ${summary.encrypted} encrypted, " +
        s"${summary.skippedNoEnc} skipped (${EnvFileEncoder.NoEncMarker}), " +
        s"${summary.alreadyEncrypted} already encrypted, ${summary.skippedEmpty} empty, " +
        s"${summary.skippedMalformed} unparseable."
    )
  }
}

case class EncodeEnvFileSpec(filePath: String)
