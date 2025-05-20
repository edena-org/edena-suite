package org.edena.ada.web.controllers.dataset

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.edena.ada.web.models.{LineWidget, Widget}
import org.edena.ada.server.models._
import org.edena.ada.server.models.ml.classification.Classifier.ClassifierIdentity
import org.edena.ada.server.models.BasicDisplayOptions
import org.edena.spark_ml.models.classification.{ClassificationEvalMetric, Classifier}
import org.edena.spark_ml.models.result._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import org.edena.ada.server.services.StatsService
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import views.html.{classificationrun => view}

import scala.reflect.runtime.universe.TypeTag
import org.edena.core.DefaultTypes.Seq

abstract class ClassificationRunControllerImpl[R <: ClassificationResult: Format : TypeTag](
  implicit actorSystem: ActorSystem, materializer: Materializer
) extends MLRunControllerImpl[R, Classifier] {

  protected def statsService: StatsService

  override protected val mlMethodName = (x: Classifier) => x.name.getOrElse("N/A")

  protected implicit val doubleScatterWidgetWrites = Widget.writes

  override protected def showView = { implicit ctx =>
    (view.show(router)(_, _, _, _)).tupled
  }

  override protected def listView = { implicit ctx =>
    (view.list(router)(_, _, _, _, _, _, _, _, _, _, _)).tupled
  }

  protected def binCurvesToWidgets(
    binCurves: Traversable[BinaryClassificationCurves],
    height: Int
  ): Traversable[Widget] = {
    def widget(title: String, xCaption: String, yCaption: String, series: Traversable[Seq[(Double, Double)]]) = {
      if (series.exists(_.nonEmpty)) {
        val data = series.toSeq.zipWithIndex.map { case (data, index) =>
          ("Run " + (index + 1).toString, data)
        }
        val displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(height))
        val widget = LineWidget[Double, Double](
          title, "", xCaption, yCaption, FieldTypeId.Double, FieldTypeId.Double, data, Some(0), Some(1), Some(0), Some(1), displayOptions)
        Some(widget)
        //      ScatterWidget(title, xCaption, yCaption, data, BasicDisplayOptions(gridWidth = Some(6), height = Some(450)))
      } else
        None
    }

    val rocWidget = widget("ROC", "FPR", "TPR", binCurves.map(_.roc))
    val prWidget = widget("PR", "Recall", "Precision", binCurves.map(_.precisionRecall))
    val fMeasureThresholdWidget = widget("FMeasure by Threshold", "Threshold", "F-Measure", binCurves.map(_.fMeasureThreshold))
    val precisionThresholdWidget = widget("Precision by Threshold", "Threshold", "Precision", binCurves.map(_.precisionThreshold))
    val recallThresholdWidget = widget("Recall by Threshold", "Threshold", "Recall", binCurves.map(_.recallThreshold))

    Seq(rocWidget, prWidget, fMeasureThresholdWidget, precisionThresholdWidget, recallThresholdWidget).flatten
  }
}