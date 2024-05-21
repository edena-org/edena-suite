package org.edena.store.mongo.docinfo

import org.edena.store.json.BSONObjectIdentity

import java.util.Date
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

final case class DocInfo(
  _id: Option[BSONObjectID] = None,
  fileName: String,
  text: String,
  charCount: Int,
  wordCount: Int,
  comment: Option[String],
  timeCreated: Date = new Date()
)

object DocInfo {

  implicit object DocInfoIdentity extends BSONObjectIdentity[DocInfo] {
    override def of(entity: DocInfo) = entity._id
    override protected def set(entity: DocInfo, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit val docInfoFormat = Json.format[DocInfo]
}