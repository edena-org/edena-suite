package org.edena.ada.web.controllers.dataset

import org.edena.ada.web.models.security.DataSetPermission
import org.edena.play.controllers.SecureControllerDispatcher
import org.edena.play.security.SecurityRole

import org.edena.core.DefaultTypes.Seq

abstract class DataSetLikeDispatcher[C](
  controllerName: ControllerName.Value
) extends SecureControllerDispatcher[C]("dataSet") {

  protected def dscf: DataSetControllerFactory
  protected def controllerFactory: (String) => C

  override protected def getController(id: String) =
    dscf(id).map(_ => controllerFactory(id)).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array(SecurityRole.admin))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(DataSetPermission(controllerId, controllerName, actionName))
}