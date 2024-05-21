package org.edena.play.security

import java.security.MessageDigest

import org.edena.play.security.ActionSecurity.AuthenticatedAction
import play.api.mvc._
import play.api.mvc.Results._

/**
  * Utils for controller/action access control and security provided mostly by the Deadbolt.
  */
object SecurityUtil {

  def toAuthenticatedAction[A](action: Action[A]): AuthenticatedAction[A] = {
    implicit request => action.apply(request)
  }

  /**
    * Calculate md5 (e.g. for encrypting passwords).
    *
    * @param in input String.
    * @return hashed string
    */
  def md5(in: String) : String = {
    if(in.isEmpty){
      new String("")
    }else{
      val md: Array[Byte] = MessageDigest.getInstance("MD5").digest(in.getBytes)
      new String(md)
    }
  }

  /**
    * Calculate sha-1 (e.g. for encrypting passwords).
    *
    * @param in input String.
    * @return hashed string
    */
  def sha1(in: String) : String = {
    if(in.isEmpty){
      new String("")
    }else{
      val md: Array[Byte] = MessageDigest.getInstance("SHA-1").digest(in.getBytes)
      new String(md)
    }
  }
}