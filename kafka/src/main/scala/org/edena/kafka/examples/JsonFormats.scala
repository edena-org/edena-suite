package org.edena.kafka.examples

import play.api.libs.json.{Format, Json}

object JsonFormats {
  implicit lazy val authorFormat: Format[Author] = Json.format[Author]
  implicit lazy val valueFormat: Format[Article] = Json.format[Article]
  implicit lazy val userFormat: Format[User] = Json.format[User]
}
