package org.edena.store.json

import org.edena.json.util.{JsonHelper => CoreJsonHelper}
import play.api.libs.json.{JsValue, Json}
import reactivemongo.api.bson.BSONObjectID

trait JsonHelper extends CoreJsonHelper {

  override def toJson(value: Any): JsValue =
    value match {
      case x: BSONObjectID => Json.toJson(x)
      case _ => super.toJson(value)
    }
}