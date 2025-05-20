package org.edena.ada.web.controllers.core

import org.edena.ada.web.models.security.DeadboltUser
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import org.edena.play.controllers.{BaseController, CrudControllerImpl, ReadonlyControllerImpl}
import play.api.libs.json.Format
import play.api.i18n.I18nSupport

import org.edena.core.DefaultTypes.Seq

abstract class AdaReadonlyControllerImpl[E: Format, ID] extends ReadonlyControllerImpl[E, ID] with AdaExceptionHandler {
  override type USER = DeadboltUser
}

abstract class AdaCrudControllerImpl[E: Format, ID](
  store: CrudStore[E, ID])(
  implicit identity: Identity[E, ID]
) extends CrudControllerImpl[E, ID](store) with AdaExceptionHandler {
  override type USER = DeadboltUser
}

trait AdaBaseController extends BaseController with I18nSupport {
  override type USER = DeadboltUser
}