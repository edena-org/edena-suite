package org.edena.ada.web.controllers.dataset

import org.edena.play.controllers.CrudController
import play.api.mvc.{Action, AnyContent}
import reactivemongo.api.bson.BSONObjectID

trait CategoryController extends CrudController[BSONObjectID] {

  def getCategoryD3Root: Action[AnyContent]

  def saveForName(name: String): Action[AnyContent]

  def relocateToParent(
    id: BSONObjectID,
    parentId: Option[BSONObjectID]
  ): Action[AnyContent]

  def idAndNames: Action[AnyContent]

  def addFields(
    categoryId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def updateLabel(
    id: BSONObjectID,
    label: String
  ): Action[AnyContent]
}