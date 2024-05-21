package org.edena.ada.web.controllers.dataset.datatrans

import org.edena.json.{EnumFormat, TupleFormat}
import org.edena.ada.server.models.Field
import org.edena.ada.server.models.datatrans.ChangeFieldTypesDataSetTransformation
import org.edena.core.field.FieldTypeId
import org.edena.play.controllers.WebContext
import org.edena.play.formatters.JsonFormatter
import play.api.data.Forms._
import views.html.{datasettrans => view}

object ChangeFieldTypesFormViews extends DataSetTransformationFormViews[ChangeFieldTypesDataSetTransformation] {

  private implicit val fieldTypeFormat = EnumFormat(FieldTypeId)
  private implicit val tupleFormat = TupleFormat[String, String, FieldTypeId.Value]
  private implicit val tupleFormatter = JsonFormatter[(String, String, FieldTypeId.Value)]

  override protected val extraMappings =
    Seq(
      // TODO: ugly.. map directly to Field
      "newFields" -> seq(of[(String, String, FieldTypeId.Value)]).transform[Seq[Field]](
        _.map { case (name, label, fieldType) => Field(name, Some(label), fieldType)},
        _.map { field: Field => (field.name, field.label.getOrElse(""), field.fieldType) }
      )
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.changeFieldTypesElements(idForm.id, idForm.form)
}