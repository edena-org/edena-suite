package org.edena.ada.web.util

import org.edena.ada.server.models.{URLType, Field}
import play.api.libs.json.JsValue
import play.twirl.api.{Html, HtmlFormat}
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.core.field.FieldTypeId
import play.api.Configuration
import reactivemongo.api.bson.BSONObjectID
import views.html.dataset.{renderers => rendererView}

import scala.collection.immutable.{Seq => ISeq}

trait FieldTypeRenderer {
  def apply(json: Option[JsValue]): Html
}

private class FieldTypeRendererImpl(field: Field) extends FieldTypeRenderer {
  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private val fieldType = ftf(field.fieldTypeSpec)

  override def apply(json: Option[JsValue]): Html = {
    val displayString = json.map(fieldType.jsonToDisplayString).getOrElse("")
    Html(displayString)
  }
}

object FieldTypeRenderer {
  def apply(field: Field): FieldTypeRenderer = new FieldTypeRendererImpl(field)
}

object FieldTypeFullRenderer {
  type FieldTypeFullInput = (Option[JsValue], String, Option[BSONObjectID])
  type FieldTypeFullRenderer = FieldTypeFullInput => Html

  private def jsonRender(fieldLabel: String): FieldTypeFullRenderer = {
    input: FieldTypeFullInput =>
      input._3.map { id =>
        rendererView.jsonFieldLink(id, input._2, fieldLabel, false)
      }.getOrElse(
        Html("")
      )
  }

  private def arrayRender(
    fieldLabel: String)(
    implicit configuration: Configuration
  ) : FieldTypeFullRenderer = {
    input: FieldTypeFullInput =>
      input._3.map { id =>
        HtmlFormat.fill(ISeq(
          rendererView.jsonFieldLink(id, input._2, fieldLabel, true),
          rendererView.arrayFieldLink(id, input._2, fieldLabel)
        ))
      }.getOrElse(
        Html("")
      )
  }

  private def jsonArrayRender(
    fieldLabel: String)(
    implicit configuration: Configuration
  ) : FieldTypeFullRenderer = {
    input: FieldTypeFullInput =>
      input._3.map { id =>
        HtmlFormat.fill(ISeq(
          rendererView.jsonFieldLink(id, input._2, fieldLabel, true),
          rendererView.arrayFieldLink(id, input._2, fieldLabel)
        ))
      }.getOrElse(
        Html("")
      )
  }

  def apply(
    field: Field)(
    implicit configuration: Configuration
  ): FieldTypeFullRenderer =
    if (field.isArray) {
      if (field.fieldType == FieldTypeId.Json)
        jsonArrayRender(field.labelOrElseName)
      else
        arrayRender(field.labelOrElseName)
    } else {
      field.fieldType match {
        case FieldTypeId.Json =>
          jsonRender(field.labelOrElseName)

        case FieldTypeId.String if field.displayAsURLType.nonEmpty =>
          input => rendererView.urlLink(
            input._1,
            field.displayAsURLType.getOrElse(URLType.GET)
          )
        case _ =>
          val renderer = FieldTypeRenderer(field)
          input => renderer(input._1)
      }
    }
}