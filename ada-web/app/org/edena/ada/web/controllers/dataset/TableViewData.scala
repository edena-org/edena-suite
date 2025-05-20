package org.edena.ada.web.controllers.dataset

import org.edena.play.Page
import org.edena.ada.server.models._
import play.api.libs.json.JsObject

import org.edena.core.DefaultTypes.Seq

case class TableViewData(
  page: Page[JsObject],
  filter: Option[Filter],
  tableFields: Traversable[Field]
)