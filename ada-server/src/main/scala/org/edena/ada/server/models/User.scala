package org.edena.ada.server.models

import org.edena.store.json.BSONObjectIdentity
import org.edena.json.SerializableFormat
import play.api.libs.json.{Json, _}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

/**
  *  User object - holds info about a user such as userId, email, roles, and  permissions.
  *
  * @param _id BSON ID of entry/ user
  * @param userId User's id.
  * @param oidcUserName contains username identifier typically for LDAP
  * @param name Full user's name
  * @param email Email of user (can be used to send notifications.
  * @param roles Roles for Deadbolt.
  * @param permissions Permissions for Deadbolt.
  */
case class User(
  _id: Option[BSONObjectID] = None,
  userId: String,
  oidcUserName: Option[String] = None,
  name: String,
  email: String,
  roles: Seq[String] = Nil,
  permissions: Seq[String] = Nil,
  locked: Boolean = false,
  passwordHash: Option[String] = None
)

object User {
  val userFormat: Format[User] = Json.format[User]
  implicit val serializableUserFormat: Format[User] = new SerializableFormat(userFormat.reads, userFormat.writes)

  implicit object UserIdentity extends BSONObjectIdentity[User] {
    def of(entity: User): Option[BSONObjectID] = entity._id
    protected def set(entity: User, id: Option[BSONObjectID]) = entity.copy(id)
  }
}
