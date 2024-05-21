package org.edena.ada.server.dataaccess.ignite

import play.api.libs.json.Json

import org.edena.store.ignite.BinaryJsonHelper
import play.api.libs.json._
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

@Deprecated
object BinaryJsonUtil extends BinaryJsonHelper {

  override def toJson(value: Any): JsValue =
    value match {
      case x: BSONObjectID => Json.toJson(x)
      case _ => super.toJson(value)
    }
}
