package org.edena.ada.web.services.widgetgen

import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models._
import org.edena.ada.web.models.LineWidget
import org.edena.ada.web.util.shorten
import org.edena.core.field.FieldTypeId
import org.edena.core.calc.impl._

import scala.reflect.runtime.universe._
import org.edena.core.DefaultTypes.Seq

private class GroupLineWidgetGenerator[G: TypeTag]
  extends CalculatorWidgetGenerator[XLineWidgetSpec, LineWidget[Any, Any], GroupXOrderedSeqCalcTypePack[G, Any]]
    with NoOptionsCalculatorWidgetGenerator[XLineWidgetSpec] {

  override protected val seqExecutor = groupXOrderedSeqAnyExec[G]

  override protected val supportArray = false

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def apply(
    spec: XLineWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (xSeq: GroupXOrderedSeqCalcTypePack[G, Any]#OUT) =>
      if (xSeq.nonEmpty) {
        // aux function
        def getFieldSafe(name: String) = fieldNameMap.get(name).getOrElse(
            throw new AdaException(s"X field '${name}' not found.")
          )

        val xField = getFieldSafe(spec.xFieldName)
        val yField = spec.yFieldNames.headOption.map(getFieldSafe)
        val groupField = getFieldSafe(spec.groupFieldName.getOrElse(
          throw new AdaException(s"Group field undefined but expected.")
        ))

        val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

        val data = spec.yFieldNames.zipWithIndex.flatMap { case (yFieldName, index) =>
          val yField = getFieldSafe(yFieldName)

          xSeq.map { case (group, xSeq) =>
            val xPartialSeq = xSeq.flatMap { case (x, seq) => seq(index).map((x, _)) }
            val groupString = group match {
              case Some(group) => groupFieldType.valueToDisplayString(Some(group))
              case None => "Undefined"
            }
            val caption = if (spec.yFieldNames.size == 1) groupString else groupString + ": " + yField.labelOrElseName

            (caption, xPartialSeq.toSeq)
          }
        }.filter(_._2.nonEmpty).sortBy(_._1)

        val yFieldLabels = spec.yFieldNames.map(name => getFieldSafe(name).labelOrElseName).mkString(", ")
        val titleS = shorten(s"${xField.labelOrElseName} vs. ${shorten(yFieldLabels, 50)} by ${groupField.labelOrElseName}", 90)

        val yAxisCaption = if (spec.yFieldNames.size == 1)
          getFieldSafe(spec.yFieldNames.head).labelOrElseName
        else
          "Value"

        val widget = LineWidget[Any, Any](
          title(spec).getOrElse(titleS),
          spec.xFieldName,
          xAxisCaption = xField.labelOrElseName,
          yAxisCaption = yAxisCaption,
          xFieldType = xField.fieldType,
          yFieldType = yField.map(_.fieldType).getOrElse(FieldTypeId.Null),
          data = data,
          displayOptions = spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object GroupLineWidgetGenerator {

  type GEN[G] = CalculatorWidgetGenerator[XLineWidgetSpec, LineWidget[Any,Any], GroupXOrderedSeqCalcTypePack[G, Any]]

  def apply[G: TypeTag]: GEN[G] = new GroupLineWidgetGenerator[G]
}

