package org.edena.ada.server.models

import org.edena.store.json.BSONObjectIdentity
import play.api.libs.json._
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

/**
  * Container for group information.
 *
  * @param _id BSON id, if group loaded from database.
  * @param groupName Short name of group.
  * @param description More detailed description of the group.
  * @param members List of group members
  */
case class UserGroup (_id: Option[BSONObjectID], groupName: String, description: Option[String], members: Seq[String] = Nil, nested: Seq[String] = Nil)

object UserGroup {
  implicit val userGroupFormat = Json.format[UserGroup]

  implicit object UserGroupIdentity extends BSONObjectIdentity[UserGroup] {
    def of(entity: UserGroup): Option[BSONObjectID] = entity._id
    protected def set(entity: UserGroup, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}