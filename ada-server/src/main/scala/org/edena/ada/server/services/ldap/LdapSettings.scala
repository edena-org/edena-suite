package org.edena.ada.server.services.ldap

import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits._

import scala.collection.mutable.ArrayBuffer

@Singleton
class LdapSettings @Inject()(configuration: Config) {

  private var settings = ArrayBuffer[(String, Any)]()

  // switch for local ldap server or connection to remote server
  // use "local" to set up local in-memory server
  // use "remote" to set up connection to remote server
  // use "none" to disable this module completely
  // defaults to "local", if no option is given
  val mode: String = conf(_.optionalString(_), "mode", "local")

  val host: String = conf(_.optionalString(_),"host", "localhost")
  val port: Int = conf(_.optionalInt(_), "port", 389)

  // general settings
  // dit denotes the branch of the directory tree which is to be used
  // groups defines which user groups are to be used for authentication
  val dit = conf(_.optionalString(_), "dit", "cn=users,cn=accounts,dc=ada")

  val groups: Seq[String] = conf(_.optionalStringSeq(_),"groups", Nil)

  val bindDN: String = conf(_.optionalString(_),"bindDN", "cn=admin.user,dc=users," + dit)
  val bindPassword: Option[String] = conf(_.optionalString(_), "bindPassword")

  // encryption settings
  // be aware that by default, client certificates are disabled and server certificates are always trusted!
  // do not use remote mode unless you know the server you connect to!
  val encryption: String = conf(_.optionalString(_), "encryption", "none")
  val trustStore: Option[String] = conf(_.optionalString(_),"trustStore")

  val addDebugUsers: Boolean = conf(_.optionalBoolean(_),"debugusers", false)

  // time-out settings
  val connectTimeout: Option[Int] = conf(_.optionalInt(_),"connectTimeout")
  val responseTimeout: Option[Long] = conf(_.optionalLong(_), "responseTimeout")
  val pooledSchemaTimeout : Option[Long] = conf(_.optionalLong(_), "pooledSchemaTimeout")
  val abandonOnTimeout: Option[Boolean] = conf(_.optionalBoolean(_), "abandonOnTimeout")

  val recursiveDitAuthenticationSearch = conf(_.optionalBoolean(_),"recursiveDitAuthenticationSearch", false)

  def listAll: Seq[(String, Any)] = settings.map { case (path, value) => ("ldap." + path, value) }.toSeq

  private def conf[T](fun: (Config, String) => Option[T], path: String, default: T): T = {
    val value = fun(configuration, "ldap." + path).getOrElse(default)
    settings += ((path, value))
    value
  }

  private def conf[T](fun: (Config, String) => Option[T], path: String): Option[T] = {
    val value = fun(configuration, "ldap." + path)
    settings += ((path, value))
    value
  }
}
