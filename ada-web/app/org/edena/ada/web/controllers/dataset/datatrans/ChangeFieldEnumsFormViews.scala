package org.edena.ada.web.controllers.dataset.datatrans

import org.edena.json.TupleFormat
import org.edena.ada.server.models.datatrans.ChangeFieldEnumsTransformation
import org.edena.play.controllers.{IdForm, WebContext}
import org.edena.play.formatters.JsonFormatter
import play.api.data.Forms._
import views.html.{datasettrans => view}

import org.edena.core.DefaultTypes.Seq

object ChangeFieldEnumsFormViews extends DataSetMetaTransformationFormViews[ChangeFieldEnumsTransformation] {

  private implicit val tupleFormat = TupleFormat[String, String, String]
  private implicit val tupleFormatter = JsonFormatter[(String, String, String)]

  override protected val extraMappings =
    Seq(
      "fieldNameOldNewEnums" -> seq(of[(String, String, String)])
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.changeFieldEnumsElements(idForm.form)
}