package org.edena.ada.web.security.play2auth

import scala.annotation.tailrec
import scala.util.Random
import java.security.SecureRandom
import scala.concurrent.duration._

import play.api.cache.SyncCacheApi

import scala.reflect.ClassTag

class CacheIdContainer[Id: ClassTag](cache: SyncCacheApi) extends IdContainer[Id] {

  private[play2auth] val tokenSuffix = ":token"
  private[play2auth] val userIdSuffix = ":userId"
  private[play2auth] val random = new Random(new SecureRandom())

  def startNewSession(userId: Id, timeoutInSeconds: Int): AuthenticityToken = {
    removeByUserId(userId)
    val token = generate
    store(token, userId, timeoutInSeconds)
    token
  }

  @tailrec
  private[play2auth] final def generate: AuthenticityToken = {
    val table = "abcdefghijklmnopqrstuvwxyz1234567890_.~*'()"
    val token = Iterator.continually(random.nextInt(table.size)).map(table).take(64).mkString
    if (get(token).isDefined) generate else token
  }

  private[play2auth] def removeByUserId(userId: Id) {
    cache.get[String](userId.toString + userIdSuffix) foreach unsetToken
    unsetUserId(userId)
  }

  def remove(token: AuthenticityToken) {
    get(token) foreach unsetUserId
    unsetToken(token)
  }

  private[play2auth] def unsetToken(token: AuthenticityToken) {
    cache.remove(token + tokenSuffix)
  }
  private[play2auth] def unsetUserId(userId: Id) {
    cache.remove(userId.toString + userIdSuffix)
  }

  def get(token: AuthenticityToken) = cache.get[Id](token + tokenSuffix)

  private[play2auth] def store(token: AuthenticityToken, userId: Id, timeoutInSeconds: Int) {
    cache.set(token + tokenSuffix, userId, timeoutInSeconds seconds)
    cache.set(userId.toString + userIdSuffix, token, timeoutInSeconds seconds)
  }

  def prolongTimeout(token: AuthenticityToken, timeoutInSeconds: Int) {
    get(token).foreach(store(token, _, timeoutInSeconds))
  }
}