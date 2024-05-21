package org.edena.ada.web.models.security

import be.objectify.deadbolt.scala.models.Subject
import org.edena.ada.server.models.User
import org.edena.play.security.{SecurityPermission, SecurityRole}

case class DeadboltUser(user: User) extends Subject {
  override def identifier =
    user.userId

  override def roles =
    user.roles.map(SecurityRole(_)).toList

  override def permissions =
    user.permissions.map(SecurityPermission(_)).toList

  val id = user._id

  val isAdmin = user.roles.contains(SecurityRole.admin)
}
