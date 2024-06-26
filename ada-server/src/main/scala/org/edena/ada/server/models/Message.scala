package org.edena.ada.server.models

import java.util.Date

import org.edena.store.json.BSONObjectIdentity
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

case class Message(
  _id: Option[BSONObjectID],
  content: String,
  createdByUser: Option[String] = None, // no user means a system message
  isUserAdmin: Boolean = false,
  timeCreated: Date = new Date()
)

object Message {
  implicit val MessageFormat = Json.format[Message]

  implicit object MessageIdentity extends BSONObjectIdentity[Message] {
    def of(entity: Message): Option[BSONObjectID] = entity._id
    protected def set(entity: Message, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}
