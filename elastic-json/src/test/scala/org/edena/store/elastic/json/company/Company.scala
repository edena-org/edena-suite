package org.edena.store.elastic.json.company

import java.util.{Date, UUID}
import org.edena.core.UUIDIdentity
import play.api.libs.json.Json

final case class Company(
  id: Option[UUID] = None,
  name: String,
  industry: String,
  director: Employee,
  owner: Employee,
  employeeCount: Int,
  revenue: Double,
  founded: Date
)

object Company {
  import Employee.employeeFormat

  implicit object CompanyIdentity extends UUIDIdentity[Company] {
    override val name = "id"
    override def of(entity: Company) = entity.id
    override protected def set(entity: Company, id: Option[UUID]) = entity.copy(id = id)
  }

  implicit val companyFormat = Json.format[Company]
}