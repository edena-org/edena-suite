package org.edena.ada.web.models

import org.edena.ada.server.dataaccess.AdaConversionException
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.store.json.JsObjectIdentity
import play.api.libs.json.{JsObject, JsString, JsValue, Json, Writes}
import org.edena.store.json.BSONObjectIDFormat

private[models] class WidgetWrites[T] extends Writes[Widget] {

  import org.edena.ada.web.models.Widget._

  private val concreteClassFieldName = "concreteClass"

  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private def fieldType(fieldTypeId: FieldTypeId.Value) =
    ftf(FieldTypeSpec(fieldTypeId)).asValueOf[T]

  private val doubleFieldType = fieldType(FieldTypeId.Double)

  override def writes(o: Widget): JsValue = {
    val concreteClassName = o.getClass.getName

    // widget as a json
    val json = o match {
      case e: CategoricalCountWidget =>
        Json.format[CategoricalCountWidget].writes(e)

      case e: NumericalCountWidget[T]  =>
        try {
          numericalCountWidgetFormat(fieldType(e.fieldType)).writes(e)
        } catch {
          case _: AdaConversionException =>
            // if the conversion fails let's try double
            numericalCountWidgetFormat(doubleFieldType).writes(e)
        }

      case e: LineWidget[T, T]  =>
        lineWidgetFormat(fieldType(e.xFieldType), fieldType(e.yFieldType)).writes(e)

      case e: ScatterWidget[T, T] =>
        scatterWidgetFormat(fieldType(e.xFieldType), fieldType(e.yFieldType)).writes(e)

      case e: ValueScatterWidget[T, T, T] =>
        valueScatterWidgetFormat(fieldType(e.xFieldType), fieldType(e.yFieldType), fieldType(e.valueFieldType)).writes(e)

      case e: BoxWidget[T] =>
        boxWidgetWrites(fieldType(e.fieldType)).writes(e)

      case e: HeatmapWidget =>
        heatmapWidgetFormat.writes(e)

      case e: BasicStatsWidget =>
        basicStatsWidgetFormat.writes(e)

      case e: IndependenceTestWidget =>
        independenceTestWidgetFormat.writes(e)

      case e: HtmlWidget =>
        Json.format[HtmlWidget].writes(e)

      case e: CategoricalCheckboxCountWidget =>
        categoricalCheckboxWidgetFormat.writes(e)
    }

    json.asInstanceOf[JsObject] ++ Json.obj(
      concreteClassFieldName -> JsString(concreteClassName),
      JsObjectIdentity.name -> o._id
    )
  }
}