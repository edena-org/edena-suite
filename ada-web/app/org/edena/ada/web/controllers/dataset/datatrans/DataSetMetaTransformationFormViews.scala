package org.edena.ada.web.controllers.dataset.datatrans

import java.util.Date

import org.edena.core.store.StreamSpec
import org.edena.ada.server.models._
import org.edena.ada.server.models.datatrans.{DataSetMetaTransformation, ResultDataSetSpec}
import org.edena.ada.web.controllers.MappingHelper
import org.edena.ada.web.controllers.core.GenericMapping
import org.edena.core.util.toHumanReadableCamel
import org.edena.core.util.hasNonAlphanumericUnderscore
import org.edena.play.controllers.{CreateEditFormViews, IdForm, WebContext}
import org.edena.play.formatters.{EnumFormatter, MapJsonFormatter, SeqFormatter}
import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.twirl.api.Html
import reactivemongo.api.bson.BSONObjectID
import views.html.{layout, datasettrans => view}

import scala.collection.Traversable
import scala.reflect.runtime.universe.{TypeTag, typeOf}

abstract protected[controllers] class DataSetMetaTransformationFormViews[E <: DataSetMetaTransformation: TypeTag](
  implicit manifest: Manifest[E]
) extends CreateEditFormViews[E, BSONObjectID] with MappingHelper {

  private val domainNameSuffix = "Transformation"
  private val humanReadableSuffix = toHumanReadableCamel(domainNameSuffix)
  protected[controllers] val displayName: String = toHumanReadableCamel(simpleClassName.replaceAllLiterally(domainNameSuffix, ""))
  protected val className =  manifest.runtimeClass.getName

  protected implicit val seqFormatter = SeqFormatter()
  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val filterShowFieldStyleFormatter = EnumFormatter(FilterShowFieldStyle)
  private implicit val widgetGenerationMethodFormatter = EnumFormatter(WidgetGenerationMethod)

  protected val extraMappings: Traversable[(String, Mapping[_])] = Nil

  protected[controllers] lazy val form: Form[E] = Form(
    GenericMapping.applyCaseClass[E](
      typeOf[E],
      Seq(
        "_id" -> ignored(Option.empty[BSONObjectID]),
        "scheduled" -> boolean,
        "scheduledTime" -> optional(scheduledTimeMapping),
        "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
        "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
      ) ++ extraMappings
    ).verifying(
      "Transformation is marked as 'scheduled' but no time provided",
      transformationInfo => (!transformationInfo.scheduled) || (transformationInfo.scheduledTime.isDefined)
    )
  )

  protected def viewElements(
    implicit webContext: WebContext
  ): OptionalIdForm[E] => Html

  protected def editViews(
    idForm: OptionalIdForm[E])(
    implicit webContext: WebContext
  ) =
    view.editMeta(idForm.form, className)(viewElements(webContext)(idForm))(webContext.msg)

  protected val defaultCreateInstance: Option[() => E] = None

  override protected[controllers] def fillForm(item: E) =
    form.fill(item)

  override protected def createView = { implicit ctx: WebContext =>
    form: CreateViewData =>
      val filledForm = if (form.hasErrors) form else defaultCreateInstance.map(x => form.fill(x())).getOrElse(form)

      layout.create(
        displayName + " " + humanReadableSuffix,
        messagePrefix,
        filledForm,
        editViews(OptionalIdForm(None, filledForm)),
        routes.DataSetTransformationController.save,
        routes.DataSetTransformationController.listAll()
      )
  }

  override protected def editView = { implicit ctx: WebContext =>
    data: IdForm[BSONObjectID, E] =>
      layout.edit(
        displayName + " " + humanReadableSuffix,
        messagePrefix,
        data.form.errors,
        editViews(OptionalIdForm(Some(data.id), data.form)),
        routes.DataSetTransformationController.update(data.id),
        routes.DataSetTransformationController.listAll(),
        Some(routes.DataSetTransformationController.delete(data.id))
      )
  }
}

case class OptionalIdForm[E](id: Option[BSONObjectID], form: Form[E])