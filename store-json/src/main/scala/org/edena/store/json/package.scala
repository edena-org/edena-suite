package org.edena.store

import play.api.libs.json.{Format, JsObject, Json}
import reactivemongo.api.bson.BSONObjectID
import reactivemongo.play.json.compat.bson2json.{fromReader, fromWriter}

package object json extends JsonHelper {
  implicit val BSONObjectIDFormat: Format[BSONObjectID] = Format[BSONObjectID](
    fromReader[BSONObjectID], fromWriter[BSONObjectID]
  )

  implicit object JsObjectIdentity extends BSONObjectIdentity[JsObject] {
    override def of(json: JsObject): Option[BSONObjectID] =
      (json \ name).toOption.flatMap(_.asOpt[BSONObjectID])

    override protected def set(json: JsObject, id: Option[BSONObjectID]): JsObject =
      json.+(name, Json.toJson(id))
  }
}
