package org.edena.ada.web.controllers.dataset

import org.edena.ada.server.models.Filter
import org.edena.play.controllers.CrudController
import play.api.mvc.{Action, AnyContent}
import reactivemongo.api.bson.BSONObjectID

trait FilterController extends CrudController[BSONObjectID] {
  def saveAjax(filter: Filter): Action[AnyContent]
  def idAndNames: Action[AnyContent]
  def idAndNamesAccessible: Action[AnyContent]
}