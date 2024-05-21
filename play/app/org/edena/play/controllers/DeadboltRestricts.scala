package org.edena.play.controllers

import be.objectify.deadbolt.scala.views.html.di._
import javax.inject.{Inject, Singleton}

@Singleton
class DeadboltRestricts @Inject() (
  val dynamic: dynamic,
  val dynamicOr: dynamicOr,
  val pattern: pattern,
  val patternOr: patternOr,
  val restrict: restrict,
  val restrictOr: restrictOr,
  val subjectNotPresent: subjectNotPresent,
  val subjectNotPresentOr: subjectNotPresentOr,
  val subjectPresent: subjectPresent,
  val subjectPresentOr: subjectPresentOr
)