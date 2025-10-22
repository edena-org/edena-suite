package org.edena.scripting

import java.io.OutputStream
import org.slf4j.LoggerFactory

class LoggerOutputStream(loggerName: String, isErr: Boolean = false) extends OutputStream {
  private val logger = LoggerFactory.getLogger(loggerName)
  private val buf = new StringBuilder

  override def write(b: Int): Unit = {
    val ch = b.toChar
    buf.append(ch)
    if (ch == '\n') flushLine()
  }
  private def flushLine(): Unit = {
    val line = buf.result().stripSuffix("\n").stripSuffix("\r")
    if (line.nonEmpty) {
      if (isErr) logger.error(line) else logger.info(line)
    }
    buf.clear()
  }
  override def flush(): Unit = if (buf.nonEmpty) flushLine()
  override def close(): Unit = flush()
}
