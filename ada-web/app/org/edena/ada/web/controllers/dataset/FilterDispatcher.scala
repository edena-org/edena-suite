package org.edena.ada.web.controllers.dataset

import be.objectify.deadbolt.scala.DeadboltHandler
import javax.inject.Inject
import org.edena.ada.web.controllers.core.AdminOrOwnerControllerDispatcherExt
import org.edena.ada.server.AdaException
import org.edena.ada.server.models.Filter
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request}
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.FilterCondition

import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.core.DefaultTypes.Seq

class FilterDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: FilterControllerFactory,
  dsaf: DataSetAccessorFactory,
  val controllerComponents: ControllerComponents
) extends DataSetLikeDispatcher[FilterController](ControllerName.filter)
   with AdminOrOwnerControllerDispatcherExt[FilterController]
   with FilterController {

  override protected val noCaching = true

  override def controllerFactory = factory(_)

  override def get(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.update(id))

  override def edit(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.edit(id))

  override def delete(id: BSONObjectID) = dispatchIsAdminOrPermissionAndOwner(id, _.delete(id))

  override def save = dispatch(_.save)

  override def saveAjax(filter: Filter) = dispatchAjax(_.saveAjax(filter))

  override def idAndNames = dispatchIsAdmin(_.idAndNames)

  override def idAndNamesAccessible  = dispatchAjax(_.idAndNamesAccessible)

  protected def dispatchIsAdminOrPermissionAndOwner(
    id: BSONObjectID,
    action: FilterController => Action[AnyContent],
    outputHandler: DeadboltHandler = handlerCache()
  ): Action[AnyContent] =
    dispatchIsAdminOrPermissionAndOwnerAux(filterOwner(id), outputHandler)(action)

  private def filterOwner(id: BSONObjectID) = {
    request: Request[AnyContent] =>
      val dataSetId = getControllerId(request)
      for {
        // data set accessor
        dsa <- dsaf.getOrError(dataSetId)
        filter <- dsa.filterStore.get(id)
      } yield
        filter.flatMap(_.createdById)
  }
}