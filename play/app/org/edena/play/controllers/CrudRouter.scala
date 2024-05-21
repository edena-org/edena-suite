package org.edena.play.controllers

import play.api.mvc.Call

trait CrudRouter[ID] extends ReadonlyRouter[ID] {
  val create: Call
  val edit: (ID) => Call = get // by default equals get
  val save: Call
  val update: (ID) => Call
  val delete: (ID) => Call
}
