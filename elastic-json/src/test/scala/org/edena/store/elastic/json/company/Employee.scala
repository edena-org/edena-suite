package org.edena.store.elastic.json.company

import java.util.Date
import play.api.libs.json.Json

final case class Employee(
  firstName: String,
  lastName: String,
  email: String,
  position: String,
  salary: Double,
  hireDate: Date,
  address: Address  // Double nested field
)

object Employee {
  import Address.addressFormat

  implicit val employeeFormat = Json.format[Employee]
}