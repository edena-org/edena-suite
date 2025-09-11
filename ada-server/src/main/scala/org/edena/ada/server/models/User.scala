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
  import scala.jdk.CollectionConverters._
  
  val userFormat: Format[User] = Json.format[User]
  implicit val serializableUserFormat: Format[User] = new SerializableFormat(userFormat.reads, userFormat.writes)

  implicit object UserIdentity extends BSONObjectIdentity[User] {
    def of(entity: User): Option[BSONObjectID] = entity._id
    protected def set(entity: User, id: Option[BSONObjectID]) = entity.copy(id)
  }
  
  def fromPOJO(pojo: UserPOJO): User = {
    User(
      _id = Option(pojo.get_id()).map(BSONObjectID.parse(_).get),
      userId = pojo.getUserId,
      oidcUserName = Option(pojo.getOidcUserName),
      name = pojo.getName,
      email = pojo.getEmail,
      roles = Option(pojo.getRoles).map(_.asScala.toSeq).getOrElse(Seq.empty),
      permissions = Option(pojo.getPermissions).map(_.asScala.toSeq).getOrElse(Seq.empty),
      locked = Option(pojo.getLocked).map(_.booleanValue()).getOrElse(false),
      passwordHash = Option(pojo.getPasswordHash)
    )
  }
  
  def toPOJO(user: User): UserPOJO = {
    val pojo = new UserPOJO()
    pojo.set_id(user._id.map(_.stringify).orNull)
    pojo.setUserId(user.userId)
    pojo.setOidcUserName(user.oidcUserName.orNull)
    pojo.setName(user.name)
    pojo.setEmail(user.email)
    pojo.setRoles(user.roles.asJava)
    pojo.setPermissions(user.permissions.asJava)
    pojo.setLocked(user.locked)
    pojo.setPasswordHash(user.passwordHash.orNull)
    pojo
  }
}
