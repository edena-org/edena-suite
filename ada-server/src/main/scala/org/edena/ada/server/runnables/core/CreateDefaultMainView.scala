package org.edena.ada.server.runnables.core

import org.edena.ada.server.runnables.DsaInputFutureRunnable
import org.edena.ada.server.models._
import org.edena.core.field.FieldTypeId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class CreateDefaultMainView extends DsaInputFutureRunnable[CreateDefaultMainViewSpec] {

  override def runAsFuture(input: CreateDefaultMainViewSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      // get the fields
      fields <- dsa.fieldStore.find()

      // add rounding for the double fields (if needed) and introduce a default label
      _ <- {
        val newFields = fields.map { field =>
          field.fieldType match {
            case FieldTypeId.Double => field.copy(label = Some(field.name), displayDecimalPlaces = input.doubleDecimalPlaces)
            case _ => field.copy(label = Some(field.name))
          }
        }
        dsa.fieldStore.update(newFields)
      }

      // create and save the main view
      _ <- dsa.dataViewStore.save(mainDataView(fields, input))
    } yield
      ()

  private def mainDataView(fields: Traversable[Field], spec: CreateDefaultMainViewSpec): DataView = {
    val doubleFieldNames = fields.filter(_.fieldType == FieldTypeId.Double).map(_.name).toSeq.sorted
    val nonDoubleFieldNames = fields.filter(_.fieldType != FieldTypeId.Double).map(_.name).toSeq.sorted

    val distributionDisplayOptions = MultiChartDisplayOptions(
      chartType = Some(ChartType.Column),
      gridWidth = spec.distributionWidgetGridWidth
    )

    val distributionWidgets = doubleFieldNames.map(
      DistributionWidgetSpec(_, None, displayOptions = distributionDisplayOptions)
    )

    val boxPlotWidgets = doubleFieldNames.map(
      BoxWidgetSpec(_, None, displayOptions = BasicDisplayOptions(gridWidth = spec.boxWidgetGridWidth))
    )

    val correlationWidget = CorrelationWidgetSpec(
      fieldNames = doubleFieldNames,
      correlationType = CorrelationType.Pearson,
      displayOptions = BasicDisplayOptions(gridWidth = spec.correlationWidgetGridWidth)
    )

    def randomDoubleFieldName: String =
      doubleFieldNames(Random.nextInt(doubleFieldNames.size))

    val scatterWidget = ScatterWidgetSpec(
      randomDoubleFieldName,
      randomDoubleFieldName,
      None,
      displayOptions =  BasicDisplayOptions(gridWidth = spec.scatterWidgetGridWidth)
    )

    DataView(
      None, "Main", Nil,
      nonDoubleFieldNames ++ doubleFieldNames,
      None, None,
      distributionWidgets ++ boxPlotWidgets ++ Seq(correlationWidget, scatterWidget),
      spec.defaultElementGridWidth,
      true
    )
  }
}

case class CreateDefaultMainViewSpec(
  dataSetId: String,
  doubleDecimalPlaces: Option[Int],
  defaultElementGridWidth: Int,
  distributionWidgetGridWidth: Option[Int],
  boxWidgetGridWidth: Option[Int],
  correlationWidgetGridWidth: Option[Int],
  scatterWidgetGridWidth: Option[Int]
)