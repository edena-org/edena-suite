package org.edena.ada.web.controllers.dataset.datatrans

import java.util.Date

import org.edena.core.store.StreamSpec
import org.edena.ada.server.models._
import org.edena.ada.server.models.datatrans.{DataSetTransformation, ResultDataSetSpec}
import org.edena.ada.web.controllers.core.GenericMapping
import org.edena.play.controllers.WebContext
import org.edena.play.formatters.EnumFormatter
import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import reactivemongo.api.bson.BSONObjectID
import views.html.{datasettrans => view}

import scala.reflect.runtime.universe.{TypeTag, typeOf}
import org.edena.core.DefaultTypes.Seq

abstract protected[controllers] class DataSetTransformationFormViews[E <: DataSetTransformation: TypeTag](
  implicit manifest: Manifest[E]
) extends DataSetMetaTransformationFormViews[E] {

  private implicit val storageTypeFormatter = EnumFormatter(StorageType)

  protected val resultDataSetSpecMapping: Mapping[ResultDataSetSpec] = mapping(
    "id" -> dataSetIdMapping,
    "name" -> nonEmptyText,
    "storageType" -> of[StorageType.Value]
  )(ResultDataSetSpec.apply)(ResultDataSetSpec.unapply)

  protected val streamSpecMapping: Mapping[StreamSpec] = mapping(
    "batchSize" -> optional(number(min = 1, max = 1000)),
    "backpressureBufferSize" -> optional(number(min = 1, max = 1000)),
    "parallelism" -> optional(number(min = 1, max = 100))
  )(StreamSpec.apply)(StreamSpec.unapply)

  protected[controllers] override lazy val form: Form[E] = Form(
    GenericMapping.applyCaseClass[E](
      typeOf[E],
      Seq(
        "_id" -> ignored(Option.empty[BSONObjectID]),
        "resultDataSetSpec" -> resultDataSetSpecMapping,
        "streamSpec" -> streamSpecMapping,
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

  override protected def editViews(
    idForm: OptionalIdForm[E])(
    implicit webContext: WebContext
  ) =
    view.edit(idForm.form, className)(viewElements(webContext)(idForm))(webContext.msg)
}