package org.edena.ada.web.controllers

import javax.inject.Inject
import org.edena.ada.web.controllers.core.AdaCrudControllerImpl
import org.edena.ada.server.models.Translation
import org.edena.ada.server.models.Translation._
import org.edena.play.controllers.{AdminRestrictedCrudController, HasBasicFormCrudViews, HasFormShowEqualEditView}
import org.edena.ada.server.dataaccess.StoreTypes._
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText}
import play.api.mvc.{ControllerComponents, Request, Result}
import reactivemongo.api.bson.BSONObjectID
import views.html.{translation => view}

import org.edena.core.DefaultTypes.Seq

class TranslationController @Inject() (
  translationRepo: TranslationStore,
  val controllerComponents: ControllerComponents
  ) extends AdaCrudControllerImpl[Translation, BSONObjectID](translationRepo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasBasicFormCrudViews[Translation, BSONObjectID]
    with HasFormShowEqualEditView[Translation, BSONObjectID] {

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "original" -> nonEmptyText,
      "translated" -> nonEmptyText
    )(Translation.apply)(Translation.unapply))

  override protected val homeCall = routes.TranslationController.find()
  override protected def createView = { implicit ctx => view.create(_) }
  override protected def editView = { implicit ctx => view.edit(_) }
  override protected def listView = { implicit ctx => (view.list(_, _)).tupled }
}