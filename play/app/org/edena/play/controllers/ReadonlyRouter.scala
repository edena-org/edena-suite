package org.edena.play.controllers

import play.api.mvc.Call

import org.edena.core.DefaultTypes.Seq

trait ReadonlyRouter[ID] {
  val list: (Int, String, Seq[org.edena.core.FilterCondition]) => Call
  val plainList: Call
  val get: (ID) => Call
}
