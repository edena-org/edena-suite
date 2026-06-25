package org.edena.core.security

/**
 * Pure, testable core of the env-file encoder. Walks `export NAME=value` lines and encrypts each
 * value in place, leaving everything else (blank lines, comments, non-`export` lines) verbatim.
 *
 * A value is left untouched when any of these hold:
 *   - the line's trailing comment contains the marker token `no_enc` (opt-out; whole-word match),
 *   - the value is empty,
 *   - the value is already `enc:v1:`-prefixed (so re-running the encoder is idempotent),
 *   - the line cannot be parsed unambiguously (e.g. junk after a closing quote) — passed through
 *     untouched rather than risk dropping content.
 *
 * Each newly-encrypted variable is annotated with `# original (last 4 letters): xxxx` so the
 * source value can be eyeballed without decrypting. For very short values (≤ 4 chars) the literal
 * is redacted instead — the whole secret is never written back. Surrounding quotes, indentation
 * and line endings (incl. CRLF) are preserved; any pre-existing (non-`no_enc`) trailing comment is
 * appended after the marker.
 *
 * Only `export NAME=` lines are processed — a plain `NAME=value` assignment is left as-is (we
 * cannot tell which bare assignments are secrets). The CLI wrapper is [[EncodeEnvFileApp]].
 */
object EncodeEnvFile {

  /** Token (whole word, case-insensitive) that opts a value out of encryption. */
  val NoEncMarker = "no_enc"

  case class Summary(
    encrypted: Int = 0,
    skippedNoEnc: Int = 0,
    alreadyEncrypted: Int = 0,
    skippedEmpty: Int = 0,
    skippedMalformed: Int = 0
  )

  private val LinePattern = """^(\s*export\s+)([A-Za-z_][A-Za-z0-9_]*)=(.*)$""".r
  private val NoEncRegex = "(?i)\\bno_enc\\b".r

  /** Encode a whole file's content, preserving line structure (incl. a trailing newline). */
  def encode(content: String, crypto: SymmetricCrypto): (String, Summary) = {
    // -1 keeps a trailing empty element so a final newline is preserved on re-join.
    val lines = content.split("\n", -1).toSeq

    val (outLines, summary) = lines.foldLeft((Vector.empty[String], Summary())) {
      case ((acc, sum), line) =>
        val (newLine, newSum) = encodeLine(line, crypto, sum)
        (acc :+ newLine, newSum)
    }

    (outLines.mkString("\n"), summary)
  }

  private def encodeLine(
    rawLine: String,
    crypto: SymmetricCrypto,
    sum: Summary
  ): (String, Summary) = {
    // Preserve a CRLF line ending: process the bare content, re-append the '\r' on output.
    val (line, eol) = if (rawLine.endsWith("\r")) (rawLine.dropRight(1), "\r") else (rawLine, "")

    line match {
      case LinePattern(exportPrefix, name, rhs) =>
        splitValueAndComment(rhs) match {
          case None => // ambiguous (e.g. junk after a closing quote) — never mangle, pass through
            (rawLine, sum.copy(skippedMalformed = sum.skippedMalformed + 1))

          case Some((rawValue, quoted, quoteChar, comment)) =>
            if (NoEncRegex.findFirstIn(comment).isDefined)
              (rawLine, sum.copy(skippedNoEnc = sum.skippedNoEnc + 1))
            else if (rawValue.isEmpty)
              (rawLine, sum.copy(skippedEmpty = sum.skippedEmpty + 1))
            else if (rawValue.startsWith(SymmetricCrypto.Prefix))
              (rawLine, sum.copy(alreadyEncrypted = sum.alreadyEncrypted + 1))
            else {
              val encrypted = crypto.encrypt(rawValue)
              val valueToken = if (quoted) s"$quoteChar$encrypted$quoteChar" else encrypted

              val priorComment =
                if (comment.nonEmpty) " -- " + comment.stripPrefix("#").trim else ""
              // Never write the full secret back: only hint the last 4 chars of longer values.
              val marker =
                if (rawValue.length > 4)
                  s"# original (last 4 letters): ${rawValue.takeRight(4)}$priorComment"
                else
                  s"# encrypted (original too short to hint safely)$priorComment"

              (s"$exportPrefix$name=$valueToken   $marker$eol", sum.copy(encrypted = sum.encrypted + 1))
            }
        }

      case _ => (rawLine, sum) // blank lines, comments, anything not `export NAME=...`
    }
  }

  /**
   * Split the right-hand side of `NAME=` into (rawValue, quoted, quoteChar, comment), where
   * `comment` includes its leading `#` (or is empty). Honors single/double quotes so a `#` inside
   * a quoted value is not mistaken for a comment. Returns `None` when the line cannot be parsed
   * unambiguously — an unterminated quote, or non-comment junk after a closing quote — so the
   * caller leaves it untouched instead of silently dropping content.
   */
  private def splitValueAndComment(rhs: String): Option[(String, Boolean, Char, String)] =
    if (rhs.nonEmpty && (rhs.head == '"' || rhs.head == '\'')) {
      val q = rhs.head
      val closeIdx = rhs.indexOf(q.toInt, 1)
      if (closeIdx < 0)
        None // unterminated quote
      else {
        val inner = rhs.substring(1, closeIdx)
        val rest = rhs.substring(closeIdx + 1).trim
        if (rest.isEmpty) Some((inner, true, q, ""))
        else if (rest.startsWith("#")) Some((inner, true, q, rest))
        else None // junk after the closing quote — don't guess
      }
    } else {
      val hashIdx = indexOfComment(rhs)
      if (hashIdx >= 0)
        Some((rhs.substring(0, hashIdx).trim, false, '"', rhs.substring(hashIdx).trim))
      else
        Some((rhs.trim, false, '"', ""))
    }

  /** Index of a `#` that begins a comment (at line start or preceded by whitespace), else -1. */
  private def indexOfComment(s: String): Int = {
    var i = 0
    while (i < s.length) {
      if (s.charAt(i) == '#' && (i == 0 || s.charAt(i - 1).isWhitespace)) return i
      i += 1
    }
    -1
  }
}
