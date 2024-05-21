package org.edena.play.security

import be.objectify.deadbolt.scala.models.Role

/**
  * Implementation of the security role interface.
  * SecurityRole is just a wrapper for a String.
  */
case class SecurityRole(val name: String) extends Role

/**
  * Definitions of predefined security roles.
  * Use these for convenience.
  */
object SecurityRole {
  val admin = "admin"
  val basic = "basic"
}