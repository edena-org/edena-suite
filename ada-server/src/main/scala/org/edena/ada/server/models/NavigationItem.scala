package org.edena.ada.server.models

import play.api.libs.json.{Format, JsObject, Json}
import org.edena.json.{RuntimeClassFormat, SubTypeFormat}
import org.edena.json.SubTypeFormat

sealed trait NavigationItem

case class Link(
  label: String,
  url: String,
  displayURLType: Option[URLType.Value]
) extends NavigationItem

case class Menu(
  header: String,
  links: Seq[Link]
) extends NavigationItem

object Link {
  import DataSetFormattersAndIds.displayURLEnumTypeFormat

  // for some reason the link format must be defined separately and then imported bellow otherwise Seq[Link] format is not picked up
  implicit val linkFormat = Json.format[Link]
}

object NavigationItem {
  import Link.linkFormat

  implicit val menuFormat = Json.format[Menu]

  implicit val navigationItemFormat: Format[NavigationItem] = new SubTypeFormat[NavigationItem](
    Seq(
      RuntimeClassFormat(linkFormat),
      RuntimeClassFormat(menuFormat)
    )
  )
}