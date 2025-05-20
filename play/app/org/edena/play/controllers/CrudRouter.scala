package org.edena.play.controllers

import play.api.mvc.Call

import org.edena.core.DefaultTypes.Seq

trait CrudRouter[ID] extends ReadonlyRouter[ID] {
  val create: Call
  val edit: (ID) => Call = get // by default equals get
  val save: Call
  val update: (ID) => Call
  val delete: (ID) => Call
}
