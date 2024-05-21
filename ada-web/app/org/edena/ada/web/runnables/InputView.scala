package org.edena.ada.web.runnables

import org.edena.core.runnables.InputRunnable
import org.edena.play.controllers.WebContext
import play.api.data.{Form, Mapping}
import play.api.libs.json.Format
import play.twirl.api.Html
import org.edena.ada.web.util

import scala.collection.Traversable

trait InputView[I] {

  self: InputRunnable[I] =>

  def inputFields(
    fieldNamePrefix: Option[String] = None)(
    implicit context: WebContext
  ): Form[I] => Html

  protected def html(htmls: Html*): Html = util.html(htmls:_*)

  def explicitMappings(fieldNamePrefix: Option[String]): Traversable[(String, Mapping[_])] = Nil
}
