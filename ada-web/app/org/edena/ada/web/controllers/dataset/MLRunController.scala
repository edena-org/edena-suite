package org.edena.ada.web.controllers.dataset

import org.edena.core.FilterCondition
import org.edena.play.controllers.ReadonlyController
import org.edena.spark_ml.models.setting.ClassificationRunSpec
import play.api.mvc.{Action, AnyContent}
import reactivemongo.api.bson.BSONObjectID

import org.edena.core.DefaultTypes.Seq

trait MLRunController extends ReadonlyController[BSONObjectID]{

  def create: Action[AnyContent]

  def delete(id: BSONObjectID): Action[AnyContent]

  def exportToDataSet(
    targetDataSetId: Option[String],
    targetDataSetName: Option[String]
  ): Action[AnyContent]

  def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]

  def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]
}