package org.edena.ada.web.controllers.dataset

import org.edena.play.controllers.ReadonlyRouter
import play.api.mvc.Call
import reactivemongo.api.bson.BSONObjectID

import org.edena.core.DefaultTypes.Seq

trait MLRunRouter extends ReadonlyRouter[BSONObjectID] {
  val create: Call
  val delete: (BSONObjectID) => Call
  val exportToDataSet: (Option[String], Option[String]) => Call
  val exportCsv: (String, Boolean, Option[String],Seq[org.edena.core.FilterCondition], Boolean) => Call
  val exportJson: (Seq[org.edena.core.FilterCondition], Boolean) => Call
}