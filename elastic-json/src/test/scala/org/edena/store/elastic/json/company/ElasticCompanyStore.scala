package org.edena.store.elastic.json.company

import com.sksamuel.elastic4s.ElasticClient

import java.util.UUID
import javax.inject.Inject
import org.edena.store.elastic.json.format.ElasticFormatCrudStore
import org.edena.store.elastic.{ElasticCrudExtraStore, ElasticCrudStoreExtraImpl, ElasticSetting}
import org.edena.store.elastic.ElasticFieldMappingExtra._
import Company.companyFormat

private[company] class ElasticCompanyStore @Inject()(
  val client: ElasticClient
) extends ElasticFormatCrudStore[Company, UUID](
  indexName = "test_companies",
  ElasticSetting()
) with ElasticCrudStoreExtraImpl[Company, UUID] with StoreTypes.CompanyStore {

  createIndexIfNeeded

  // Full explicit mapping for all fields
  // director: objectField - ElasticReadonlyStore won't wrap queries in NestedQuery
  //   - director.address: objectField (double nested as objectField within objectField)
  // owner: nestedField - ElasticReadonlyStore will automatically wrap queries in NestedQuery
  //   - owner.address: objectField (double nested as objectField within nestedField)
  override protected def fieldDefs = Seq(
    keywordField("id") store true,
    keywordField("name") store true,
    keywordField("industry") store true,
    objectField("director") fields Seq(
      keywordField("firstName") store true,
      keywordField("lastName") store true,
      keywordField("email") store true,
      keywordField("position") store true,
      doubleField("salary") store true,
      dateField("hireDate") store true,
      objectField("address") fields Seq(
        keywordField("street") store true,
        keywordField("city") store true,
        keywordField("state") store true,
        keywordField("zipCode") store true,
        keywordField("country") store true
      )
    ),
    nestedField("owner") fields Seq(
      keywordField("firstName") store true,
      keywordField("lastName") store true,
      keywordField("email") store true,
      keywordField("position") store true,
      doubleField("salary") store true,
      dateField("hireDate") store true,
      objectField("address") fields Seq(
        keywordField("street") store true,
        keywordField("city") store true,
        keywordField("state") store true,
        keywordField("zipCode") store true,
        keywordField("country") store true
      )
    ),
    intField("employeeCount") store true,
    doubleField("revenue") store true,
    dateField("founded") store true
  )
}

object StoreTypes {
  type CompanyStore = ElasticCrudExtraStore[Company, UUID]
}
