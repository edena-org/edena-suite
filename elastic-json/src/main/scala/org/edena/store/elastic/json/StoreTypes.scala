package org.edena.store.elastic.json

import play.api.libs.json.JsObject
import org.edena.store.elastic.{ElasticCrudExtraStore, ElasticReadonlyExtraStore}
import reactivemongo.api.bson.BSONObjectID

object StoreTypes {
  type ElasticJsonCrudStore = ElasticCrudExtraStore[JsObject, BSONObjectID]
  type ElasticReadonlyCrudStore = ElasticReadonlyExtraStore[JsObject, BSONObjectID]
}