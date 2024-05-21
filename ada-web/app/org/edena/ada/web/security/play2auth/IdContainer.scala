package org.edena.ada.web.security.play2auth

trait IdContainer[Id] {

  def startNewSession(userId: Id, timeoutInSeconds: Int): AuthenticityToken

  def remove(token: AuthenticityToken): Unit
  def get(token: AuthenticityToken): Option[Id]

  def prolongTimeout(token: AuthenticityToken, timeoutInSeconds: Int): Unit

}
