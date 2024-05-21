package org.edena.play.controllers

import play.api.mvc.Call

trait ReadonlyRouter[ID] {
  val list: (Int, String, Seq[org.edena.core.FilterCondition]) => Call
  val plainList: Call
  val get: (ID) => Call
}
