package org.edena.ada.web.controllers.dataset

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import org.edena.ada.server.AdaException
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.models._
import org.edena.json.OrdinalEnumFormat
import org.edena.ada.server.models.ml.classification.ClassificationResult.temporalClassificationResultFormat
import org.edena.core.store.Criterion
import org.edena.core.store.Criterion.Infix
import org.edena.spark_ml.MLResultUtil
import org.edena.spark_ml.models.VectorScalerType
import org.edena.spark_ml.models.classification.ClassificationEvalMetric
import org.edena.spark_ml.models.result.TemporalClassificationResult
import org.edena.spark_ml.models.setting.{ClassificationRunSpec, TemporalClassificationRunSpec}
import org.edena.ada.server.dataaccess.StoreTypes.ClassifierStore
import org.edena.ada.server.field.FieldUtil.FieldOps
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents}
import org.edena.ada.server.services.ml.MachineLearningService
import org.edena.ada.server.services.StatsService
import org.edena.ada.server.services.DataSetService
import org.edena.ada.web.services.{DataSpaceService, WidgetGenerationService}
import views.html.{classificationrun => view}

import scala.concurrent.Future

class TemporalClassificationRunControllerImpl @Inject()(
  @Assisted dataSetId: String,
  dsaf: DataSetAccessorFactory,
  val mlMethodRepo: ClassifierStore,
  val mlService: MachineLearningService,
  val statsService: StatsService,
  val dataSetService: DataSetService,
  val dataSpaceService: DataSpaceService,
  val wgs: WidgetGenerationService,
  val controllerComponents: ControllerComponents)(
  implicit actorSystem: ActorSystem, materializer: Materializer
) extends ClassificationRunControllerImpl[TemporalClassificationResult]
    with TemporalClassificationRunController {

  override protected def dsa = dsaf.applySync(dataSetId).get
  override protected val store = dsa.temporalClassificationStore

  override protected val router = new TemporalClassificationRunRouter(dataSetId)

  override protected val entityNameKey = "temporalClassificationRun"
  override protected val exportFileNamePrefix = "temporal_classification_results_"
  override protected val excludedFieldNames = Seq("reservoirSetting")

  private val distributionDisplayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column), gridWidth = Some(3))

  override protected val widgetSpecs = Seq(
    DistributionWidgetSpec("testStats-accuracy-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-weightedPrecision-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-weightedRecall-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-f1-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-areaUnderROC-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-areaUnderPR-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("timeCreated", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
    ScatterWidgetSpec("trainingStats-accuracy-mean", "testStats-accuracy-mean", Some("runSpec-mlModelId")),
    ScatterWidgetSpec("testStats-areaUnderROC-mean", "testStats-accuracy-mean", Some("runSpec-mlModelId"))
  )

  override protected val listViewColumns = Some(Seq(
    "runSpec-mlModelId",
    "runSpec-ioSpec-filterId",
    "runSpec-ioSpec-outputFieldName",
    "testStats-accuracy-mean",
    "testStats-weightedPrecision-mean",
    "testStats-weightedRecall-mean",
    "testStats-f1-mean",
    "testStats-areaUnderROC-mean",
    "testStats-areaUnderPR-mean",
    "timeCreated"
  ))

  override protected def createView = { implicit ctx =>
    (view.createTemporal(_, _, _)).tupled
  }

  override def launch(
    runSpec: TemporalClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ) = Action.async { implicit request => {
    val ioSpec = runSpec.ioSpec

    val mlModelFuture = mlMethodRepo.get(runSpec.mlModelId)
    val criterionFuture = loadCriterion(runSpec.ioSpec.filterId)
    val replicationCriterionFuture = replicationCriterion(runSpec)

    val fieldNames = runSpec.ioSpec.allFieldNames
    val fieldsFuture = dsa.fieldStore.find(FieldIdentity.name #-> fieldNames)

    def find(criterion: Criterion, orderedValues: Seq[Any]) = {
      val finalCriterion = if (orderedValues.nonEmpty)
        criterion AND (ioSpec.orderFieldName #-> orderedValues) // add ordered values only
      else
        criterion

      dsa.dataSetStore.find(finalCriterion, projection = fieldNames)
    }

    for {
      // load a ML model
      mlModel <- mlModelFuture

      // criterion
      criterion <- criterionFuture

      // replication criterion
      replicationCriterion <- replicationCriterionFuture

      // fields
      fields <- fieldsFuture

      // order field
      orderField = fields.find(_.name == ioSpec.orderFieldName).getOrElse(throw new AdaException(s"Order field ${ioSpec.outputFieldName} not found."))
      orderFieldType = ftf(orderField.fieldTypeSpec).asValueOf[Any]
      orderedValues = if (ioSpec.orderedStringValues.isEmpty && (orderField.isEnum || orderField.isString)) {
        throw new AdaException(s"String (display) values in fixed order required for the ${orderField.fieldType} order field ${ioSpec.orderFieldName}.")
      } else
        ioSpec.orderedStringValues.map(x => orderFieldType.displayStringToValue(x).get)

      // main data
      mainData <- find(criterion, orderedValues)

      // replication data
      replicationData <- replicationCriterion.map(find(_, orderedValues)).getOrElse(Future(Nil))

      // select features
      selectedInputFieldNames <- runSpec.learningSetting.core.featuresSelectionNum.map { featuresSelectionNum =>
        val inputFields = fields.filter(field => ioSpec.inputFieldNames.contains(field.name))
        val outputField = fields.find(_.name == ioSpec.outputFieldName).getOrElse(throw new AdaException(s"Output field ${ioSpec.outputFieldName} not found."))

        // ordered values criteria
        val finalCriterion = if (orderedValues.nonEmpty)
          criterion AND (ioSpec.orderFieldName #-> orderedValues) // add ordered values only
        else
          criterion

        statsService.selectFeaturesAsAnovaChiSquare(
          dsa.dataSetStore,
          finalCriterion,
          inputFields.toSeq,
          outputField,
          featuresSelectionNum
        ).map {
          _.map(_.name)
        }
      }.getOrElse(
        Future(ioSpec.inputFieldNames)
      )

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val actualFieldNames = ioSpec.copy(inputFieldNames = selectedInputFieldNames).allFieldNames

        val fieldNameAndSpecs = fields.toSeq.filter(field => actualFieldNames.contains(field.name)).map(field => (field.name, field.fieldTypeSpec))

        val results = mlService.classifyRowTemporalSeries(
          mainData, fieldNameAndSpecs, selectedInputFieldNames, ioSpec.outputFieldName, ioSpec.orderFieldName, orderedValues, Some(ioSpec.groupIdFieldName),
          mlModel, runSpec.learningSetting, replicationData
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.map { resultsHolder =>
        // prepare the results stats
        val metricStatsMap = MLResultUtil.calcMetricStats(resultsHolder.performanceResults)

        if (saveResults) {
          val binCurves = if (saveBinCurves) resultsHolder.binCurves else Nil
          val finalResult = MLResultUtil.createTemporalClassificationResult(runSpec, metricStatsMap, binCurves)
          store.save(finalResult)
        }

        val resultsJson = resultsToJson(ClassificationEvalMetric)(metricStatsMap)

        val replicationCurves = binCurvesToWidgets(resultsHolder.binCurves.flatMap(_._3), 350)
        val height = if (replicationCurves.nonEmpty) 350 else 500
        val trainingCurves = binCurvesToWidgets(resultsHolder.binCurves.flatMap(_._1), height)
        val testCurves = binCurvesToWidgets(resultsHolder.binCurves.flatMap(_._2), height)

        logger.info("Classification finished with the following results:\n" + Json.prettyPrint(resultsJson))

        val json = Json.obj(
          "results" -> resultsJson,
          "trainingCurves" -> Json.toJson(trainingCurves.toSeq),
          "testCurves" -> Json.toJson(testCurves.toSeq),
          "replicationCurves" -> Json.toJson(replicationCurves.toSeq)
        )
        Ok(json)
      }.getOrElse(
        BadRequest(s"ML classification model with id ${runSpec.mlModelId.stringify} not found.")
      )
    }.recover(handleExceptionsWithErrorCodes("a launch"))
  }

  override protected def exportFormat =
    org.edena.ada.server.models.ml.classification.ClassificationResult.createTemporalClassificationResultFormat(
      OrdinalEnumFormat(VectorScalerType),
      OrdinalEnumFormat(ClassificationEvalMetric)
    )

  override protected def alterExportJson(resultJson: JsObject): JsObject = {
    // handle sampling ratios, which are stored as an unstructured array
    val newSamplingRatioJson = (resultJson \ "runSpec-learningSetting-core-samplingRatios").asOpt[JsArray].map { jsonArray =>
      val samplingRatioJsons = jsonArray.value.map { case json: JsArray =>
        val outputValue = json.value(0)
        val samplingRatio = json.value(1)
        Json.obj("outputValue" -> outputValue, "samplingRatio" -> samplingRatio)
      }
      JsArray(samplingRatioJsons)
    }.getOrElse(JsNull)

    resultJson.+("runSpec-learningSetting-core-samplingRatios", newSamplingRatioJson)
  }
}