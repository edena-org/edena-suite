package org.edena.play.security

import be.objectify.deadbolt.scala.models.Permission

/**
  * Generalized permission. Use to construct custom permissions.
  *
  * @param value descriptor of the permission. Refer to definitions in SecurityPermissionCache for samples.
  */
case class SecurityPermission(val value: String) extends Permission {

  override def toString() =
    value.substring(value.indexOf('=')+1, value.indexOf(','))
}