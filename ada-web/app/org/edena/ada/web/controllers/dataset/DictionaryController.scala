package org.edena.ada.web.controllers.dataset

import org.edena.core.FilterCondition
import org.edena.play.controllers.CrudController
import play.api.mvc.{Action, AnyContent}

trait DictionaryController extends CrudController[String] {

  def updateLabel(id: String, label: String): Action[AnyContent]

  def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]

  def exportRecordsAsJson(filter: Seq[FilterCondition], tableColumnsOnly: Boolean): Action[AnyContent]

  def setDefaultLabels: Action[AnyContent]

  def convertLabelsToCamelCase: Action[AnyContent]
}