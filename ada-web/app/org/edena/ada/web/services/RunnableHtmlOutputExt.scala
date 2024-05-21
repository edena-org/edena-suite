package org.edena.ada.web.services

import org.edena.core.runnables.RunnableHtmlOutput
import play.twirl.api.Html

trait RunnableHtmlOutputExt extends RunnableHtmlOutput {

  protected def addHtml(html: Html): Unit =
    output ++= html.toString()
}
