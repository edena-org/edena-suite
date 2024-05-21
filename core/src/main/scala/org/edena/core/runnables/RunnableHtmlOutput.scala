package org.edena.core.runnables

trait RunnableHtmlOutput {

  val output = new StringBuilder

  protected def addOutput(string: String): Unit =
    output ++= string

  protected def addParagraph(string: String): Unit =
    addOutput(s"<p>$string</p>")

  protected def addDiv(string: String): Unit =
    addOutput( s"<div>$string</div>")

  protected def bold(string: String): String =
    s"<b>$string</b>"

  protected def it(string: String): String =
    s"<i>$string</i>"
}
