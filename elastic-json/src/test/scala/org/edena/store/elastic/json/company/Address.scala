package org.edena.store.elastic.json.company

import play.api.libs.json.Json

final case class Address(
  street: String,
  city: String,
  state: String,
  zipCode: String,
  country: String
)

object Address {
  implicit val addressFormat = Json.format[Address]
}