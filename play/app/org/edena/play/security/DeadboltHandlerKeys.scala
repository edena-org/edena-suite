package org.edena.play.security

import be.objectify.deadbolt.scala.HandlerKey

/**
 *  Deadbolt handler key definitions
 */
object DeadboltHandlerKeys {

  val default = Key("defaultHandler")  // key for default handler; handler retrieves user, authority and permission information
  val unauthorizedStatus = Key("unauthorizedStatus")

  case class Key(name: String) extends HandlerKey
}