package org.edena.play

import play.api.libs.json.Json

case class PageOrder(page: Int, orderBy: String)

object PageOrder {
  implicit val format = Json.format[PageOrder]
}
