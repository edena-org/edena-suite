package org.edena.ada.web.runnables.core

import com.typesafe.config.Config
import org.edena.ada.web.runnables.InputView
import org.edena.core.runnables.{InputRunnableExt, RunnableHtmlOutput}
import org.edena.core.security.{SymmetricCrypto, EncodeEnvFile => EnvFileEncoder}
import org.edena.core.util.LoggingSupport
import org.edena.play.controllers.WebContext
import org.edena.play.controllers.WebContext._
import play.twirl.api.{Html, HtmlFormat}
import views.html.elements._

import javax.inject.Inject
import org.edena.core.DefaultTypes.Seq

/**
 * In-browser sibling of [[org.edena.ada.server.runnables.core.EncodeEnvFile]] (and the CLI
 * [[org.edena.core.security.EncodeEnvFileApp]]): paste the `export NAME=value` lines of an env file
 * into a text area and get back the same content with every plaintext value replaced by its
 * `enc:v1:` ciphertext — rendered as HTML output you can copy straight back into the file. Nothing
 * is read from or written to disk.
 *
 * `# no_enc`-marked, empty, and already-`enc:v1:` values are left untouched (so re-running is
 * idempotent), and only `export NAME=` lines are processed — see [[EnvFileEncoder]]. The master key
 * is read from the app config (`edena.encryption-key` / env `EDENA_ENCRYPTION_KEY`); execution fails
 * with a clear error if it is unset.
 */
class EncodeEnvText @Inject() (config: Config)
  extends InputRunnableExt[EncodeEnvTextSpec]
  with RunnableHtmlOutput
  with InputView[EncodeEnvTextSpec]
  with LoggingSupport {

  override def run(input: EncodeEnvTextSpec): Unit = {
    // Throws a clear error if no encryption master key is configured — there is no fallback key.
    val crypto = SymmetricCrypto(config)

    val (encoded, summary) = EnvFileEncoder.encode(input.envContent, crypto)

    addParagraph(
      bold("Encoded") + s": ${summary.encrypted} encrypted, ${summary.alreadyEncrypted} already " +
        s"encrypted, ${summary.skippedNoEnc} skipped (${EnvFileEncoder.NoEncMarker}), " +
        s"${summary.skippedEmpty} empty, ${summary.skippedMalformed} unparseable."
    )

    // Ciphertext is safe to render; HTML-escape so values containing < > & " display verbatim. A
    // read-only text area makes the result easy to select and copy back into the env file.
    addOutput(
      s"""<textarea readonly rows="25" cols="100" class="form-control">""" +
        HtmlFormat.escape(encoded).body +
        "</textarea>"
    )

    logger.info(
      s"Encoded pasted env content: ${summary.encrypted} encrypted, " +
        s"${summary.alreadyEncrypted} already encrypted, ${summary.skippedNoEnc} skipped (no_enc), " +
        s"${summary.skippedEmpty} empty, ${summary.skippedMalformed} unparseable."
    )
  }

  override def inputFields(
    fieldNamePrefix: Option[String] = None)(
    implicit webContext: WebContext
  ) = (form) =>
    html(
      textarea(
        "encodeEnvText",
        fieldNamePrefix.getOrElse("") + "envContent",
        form,
        Seq(
          'cols -> 100,
          'rows -> 25,
          '_label -> "Env File Content",
          '_helpModal -> Html(
            "Paste the <i>export NAME=value</i> lines of an env file. Each plaintext value is " +
              "replaced by its <code>enc:v1:</code> ciphertext; <code># no_enc</code>-marked, empty, " +
              "and already-encrypted values are left as-is. Only <i>export</i> lines are processed."
          )
        )
      )
    )
}

case class EncodeEnvTextSpec(envContent: String)
