package org.edena.kafka.examples

import java.util.UUID

case class User(
  id: UUID,
  name: String,
  email: String,
  age: Int,
//  created: LocalDate,
  isActive: Boolean,
  hobby: Option[String]
)
