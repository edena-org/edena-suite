package org.edena.play.controllers

import org.edena.play.security.HasAuthAction
import play.api.mvc.{Action, AnyContent, Request, BaseController => PlayBaseController}

/**
 * Simple dispatcher using controller id lookup to find a corresponding controller to redirect a call (function) to.
 *
 * @author Peter Banda
 */
abstract class ControllerDispatcher[C](controllerParamId: String) extends PlayBaseController with HasAuthAction {

  type DispatchActionTransformation = (C => Action[AnyContent]) => Action[AnyContent]

  protected val noActionCaching = true

  protected def getController(controllerId: String): C

  private implicit val ec = controllerComponents.executionContext

  protected def dispatch: DispatchActionTransformation = { cAction =>
    val resultAction = AuthAction { implicit request =>
      val controllerId = getControllerId(request)
      cAction(getController(controllerId)).apply(request)
    }

    if (noActionCaching)
      WithNoCaching(resultAction)
    else
      resultAction
  }

  // a helper function
  protected def getControllerId(request: Request[AnyContent]) = {
    val controllerIdOption = request.queryString.get(controllerParamId)
    controllerIdOption.getOrElse(
      throw new IllegalArgumentException(s"Controller param id '${controllerParamId}' not found.")
    ).head
  }
}

/**
  * Static controller dispatcher.
  *
  * @author Peter Banda
  */
abstract class StaticControllerDispatcher[C](
  controllerParamId: String,
  controllers : Iterable[(String, C)]
) extends ControllerDispatcher[C](controllerParamId) {

  private val idControllerMap = controllers.toMap

  override protected def getController(controllerId: String): C =
    idControllerMap.getOrElse(
      controllerId,
      throw new IllegalArgumentException(s"Controller id '${controllerId}' not recognized.")
    )
}