package org.edena.ada.server.models

import org.edena.store.json.BSONObjectIdentity
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

@Deprecated
case class Translation(
  _id : Option[BSONObjectID],
  original : String,
  translated : String
) {
  override def toString = original + " -> " + translated
}

object Translation {
  implicit val TranslationFormat = Json.format[Translation]

  implicit object TranslationIdentity extends BSONObjectIdentity[Translation] {
    def of(entity: Translation): Option[BSONObjectID] = entity._id
    protected def set(entity: Translation, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}