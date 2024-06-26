package org.edena.ada.server.runnables.core

import java.{lang => jl}

import javax.inject.Inject
import com.bnd.math.domain.rand.RandomDistribution
import com.bnd.network.domain.ActivationFunctionType
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.dataaccess.StoreTypes.RegressorStore
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.ada.server.services.ml.MachineLearningService
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.spark_ml.models.VectorScalerType
import org.edena.core.store.Criterion.Infix
import org.edena.spark_ml.models.ValueOrSeq.ValueOrSeq
import org.edena.spark_ml.models.regression.RegressionEvalMetric
import org.edena.spark_ml.models.setting.{RegressionLearningSetting, TemporalRegressionLearningSetting}
import org.edena.spark_ml.models.ReservoirSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future

class RunRowTimeSeriesRCRegression @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    regressionRepo: RegressorStore
  ) extends InputFutureRunnableExt[RunRowTimeSeriesRCRegressionSpec] with TimeSeriesResultsHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(
    input: RunRowTimeSeriesRCRegressionSpec
  ): Future[Unit] = {
    val fieldNames = (input.inputFieldNames ++ Seq(input.outputFieldName, input.orderFieldName)).toSet.toSeq

    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.dataSetId)

      // load a ML model
      mlModel <- regressionRepo.get(input.mlModelId)

      // get all the fields
      fields <- dsa.fieldStore.find(FieldIdentity.name #-> fieldNames)
      fieldNameSpecs = fields.map(field => (field.name, field.fieldTypeSpec)).toSeq

      // order field (and type)
      orderField <- dsa.fieldStore.get(input.orderFieldName).map(_.get)
      orderFieldType = ftf(orderField.fieldTypeSpec).asValueOf[Any]
      orderedValues = input.orderedStringValues.map(orderFieldType.displayStringToValue)

      // criterion field (and type)
      criterionField <- dsa.fieldStore.get(input.criterionFieldName).map(_.get)
      criterionFieldType = ftf(criterionField.fieldTypeSpec).asValueOf[Any]
      criterionValue = criterionFieldType.displayStringToValue(input.criterionStringValue).get

      // jsons
      data <- dsa.dataSetStore.find(
        criterion = input.criterionFieldName #== criterionValue,
        projection = fieldNames
      )

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val results = mlService.regressRowTemporalSeries(
          data,
          fieldNameSpecs,
          input.inputFieldNames,
          input.outputFieldName,
          input.orderFieldName,
          orderedValues,
          None,
          mlModel,
          TemporalRegressionLearningSetting(
            core = input.learningSetting,
            predictAhead = input.predictAhead,
            slidingWindowSize = Left(input.windowSize),
            reservoirSetting = Some(input.reservoirSpec),
            minCrossValidationTrainingSizeRatio = input.minCrossValidationTrainingSizeRatio,
            trainingTestSplitOrderValue = input.trainingTestSplitOrderValue
          )
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.foreach(exportResults)
  }
}

case class RunRowTimeSeriesRCRegressionSpec(
  // input/output specification
  dataSetId: String,
  inputFieldNames: Seq[String],
  outputFieldName: String,
  orderFieldName: String,
  orderedStringValues: Seq[String],
  criterionFieldName: String,
  criterionStringValue: String,
  predictAhead: Int,

  // ML model
  mlModelId: BSONObjectID,

  // delay line window size
  windowSize: Option[Int],

  // reservoir setting
  reservoirNodeNum: ValueOrSeq[Int] = Left(None),
  reservoirInDegree: ValueOrSeq[Int] = Left(None),
  reservoirEdgesNum: ValueOrSeq[Int] = Left(None),
  reservoirPreferentialAttachment: Boolean = false,
  reservoirBias: Boolean = false,
  reservoirCircularInEdges: Option[Seq[Int]] = None,
  inputReservoirConnectivity: ValueOrSeq[Double] = Left(None),
  reservoirSpectralRadius: ValueOrSeq[Double] = Left(None),
  reservoirFunctionType: ActivationFunctionType,
  reservoirFunctionParams: Seq[Double] = Nil,
  washoutPeriod: ValueOrSeq[Int] = Left(None),

  // learning setting
  learningSetting: RegressionLearningSetting,
  minCrossValidationTrainingSizeRatio: Option[Double],
  trainingTestSplitOrderValue: Option[Double]
) {
  def reservoirSpec =
    ReservoirSpec(
      inputNodeNum = learningSetting.pcaDims.getOrElse(inputFieldNames.size) * windowSize.getOrElse(1),
      bias = 1,
      nonBiasInitial = 0,
      reservoirNodeNum = reservoirNodeNum,
      reservoirInDegree = reservoirInDegree,
      reservoirEdgesNum = reservoirEdgesNum,
      reservoirPreferentialAttachment = reservoirPreferentialAttachment,
      reservoirBias = reservoirBias,
      reservoirCircularInEdges = reservoirCircularInEdges,
      inputReservoirConnectivity = inputReservoirConnectivity,
      reservoirSpectralRadius = reservoirSpectralRadius,
      reservoirFunctionType = reservoirFunctionType,
      reservoirFunctionParams = reservoirFunctionParams,
      weightDistribution = RandomDistribution.createNormalDistribution(classOf[jl.Double], 0d, 1d),
      washoutPeriod = washoutPeriod
    )
}