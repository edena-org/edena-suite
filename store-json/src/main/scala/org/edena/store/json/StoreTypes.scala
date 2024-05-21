package org.edena.store.json

import play.api.libs.json.JsObject
import org.edena.core.store._
import reactivemongo.api.bson.BSONObjectID

object StoreTypes {
  type JsonReadonlyStore = ReadonlyStore[JsObject, BSONObjectID]
  type JsonCrudStore = CrudStore[JsObject, BSONObjectID]
}