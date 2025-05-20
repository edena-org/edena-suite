package org.edena.ada.web.controllers.dataset

import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.Inject
import com.google.inject.assistedinject.Assisted
import org.edena.ada.server.models.{DistributionWidgetSpec, _}
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.edena.ada.server.models.ml.regression.RegressionResult.standardRegressionResultFormat
import org.edena.ada.web.models.Widget.writes
import org.edena.ada.server.dataaccess.StoreTypes.{ClassifierStore, RegressorStore}
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.data.Forms._
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, Request}
import org.edena.ada.server.services.DataSetService
import org.edena.ada.web.services.{DataSpaceService, WidgetGenerationService}
import org.edena.ada.server.services.ml._
import org.edena.json.OrdinalEnumFormat
import org.edena.core.store.Criterion._
import org.edena.core.store.Criterion
import org.edena.spark_ml.MLResultUtil
import org.edena.spark_ml.models.regression.RegressionEvalMetric
import org.edena.spark_ml.models.result.StandardRegressionResult
import org.edena.spark_ml.models.setting.RegressionRunSpec
import org.edena.spark_ml.models.VectorScalerType
import org.edena.ada.server.services.StatsService
import views.html.{regressionrun => view}

import scala.concurrent.{Future, TimeoutException}
import org.edena.core.DefaultTypes.Seq

protected[controllers] class StandardRegressionRunControllerImpl @Inject()(
  @Assisted dataSetId: String,
  dsaf: DataSetAccessorFactory,
  val mlMethodRepo: RegressorStore,
  val mlService: MachineLearningService,
  val dataSetService: DataSetService,
  val dataSpaceService: DataSpaceService,
  val wgs: WidgetGenerationService,
  val controllerComponents: ControllerComponents)(
  implicit actorSystem: ActorSystem, materializer: Materializer
) extends RegressionRunControllerImpl[StandardRegressionResult]
  with StandardRegressionRunController {

  override protected def dsa = dsaf.applySync(dataSetId).get
  override protected val store = dsa.standardRegressionResultStore

  override protected val router = new StandardRegressionRunRouter(dataSetId)

  override protected val entityNameKey = "regressionRun"
  override protected val exportFileNamePrefix = "regression_results_"

  private val distributionDisplayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column), gridWidth = Some(3))

  override protected val widgetSpecs = Seq(
    DistributionWidgetSpec("testStats-mse-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-rmse-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-r2-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-mae-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("timeCreated", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
    ScatterWidgetSpec("trainingStats-mse-mean", "testStats-mse-mean", Some("runSpec-mlModelId")),
    ScatterWidgetSpec("testStats-r2-mean", "testStats-mse-mean", Some("runSpec-mlModelId"))
  )

  override protected val listViewColumns = Some(Seq(
    "runSpec-mlModelId",
    "runSpec-ioSpec-filterId",
    "runSpec-ioSpec-outputFieldName",
    "testStats-mae-mean",
    "testStats-mse-mean",
    "testStats-rmse-mean",
    "testStats-r2-mean",
    "timeCreated"
  ))

  override protected def createView = { implicit ctx =>
    (view.create(_, _, _)).tupled
  }

  override def launch(
    runSpec: RegressionRunSpec,
    saveResults: Boolean
  ) = Action.async { implicit request => {
    println(runSpec)

    val mlModelFuture = mlMethodRepo.get(runSpec.mlModelId)
    val criterionFuture = loadCriterion(runSpec.ioSpec.filterId)
    val replicationCriterionFuture = replicationCriterion(runSpec)

    val fieldNames = runSpec.ioSpec.allFieldNames
    val fieldsFuture = dsa.fieldStore.find(FieldIdentity.name #-> fieldNames)

    def find(criterion: Criterion) =
      dsa.dataSetStore.find(criterion, projection = fieldNames)

    for {
      // load a ML model
      mlModel <- mlModelFuture

      // criterion
      criterion <- criterionFuture

      // replication criterion
      replicationCriterion <- replicationCriterionFuture

      // main data
      mainData <- find(criterion)

      // fields
      fields <- fieldsFuture

      // replication data
      replicationData <- replicationCriterion.map(find).getOrElse(Future(Nil))

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val fieldNameAndSpecs = fields.toSeq.map(field => (field.name, field.fieldTypeSpec))
        val results = mlService.regressStatic(mainData, fieldNameAndSpecs, runSpec.ioSpec.outputFieldName, mlModel, runSpec.learningSetting, replicationData)
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.map { resultsHolder =>
        // prepare the results stats
        val metricStatsMap = MLResultUtil.calcMetricStats(resultsHolder.performanceResults)

        if (saveResults) {
          val finalResult = MLResultUtil.createStandardRegressionResult(runSpec, metricStatsMap)
          store.save(finalResult)
        }

        val resultsJson = resultsToJson(RegressionEvalMetric)(metricStatsMap)

        logger.info("Regression finished with the following results:\n" + Json.prettyPrint(resultsJson))

        Ok(resultsJson)
      }.getOrElse(
        BadRequest(s"ML regression model with id ${runSpec.mlModelId.stringify} not found.")
      )
    }.recover(handleExceptionsWithErrorCodes("a launch"))
  }

  override protected def exportFormat=
    org.edena.ada.server.models.ml.regression.RegressionResult.createStandardRegressionResultFormat(
      OrdinalEnumFormat(VectorScalerType),
      OrdinalEnumFormat(RegressionEvalMetric)
    )
}