package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.dataaccess.StoreTypes.RegressorStore
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.services.ml.MachineLearningService
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.core.store.Criterion.Infix
import org.edena.core.store.{And, Criterion, NotEqualsNullCriterion}
import org.edena.spark_ml.models.setting.{TemporalGroupIOSpec, TemporalRegressionLearningSetting}
import org.edena.ada.server.field.FieldUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future

class RunRowTimeSeriesDLRegression @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    regressionRepo: RegressorStore
  ) extends InputFutureRunnableExt[RunRowTimeSeriesDLRegressionSpec] with TimeSeriesResultsHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(
    input: RunRowTimeSeriesDLRegressionSpec
  ): Future[Unit] = {
    val ioSpec = input.ioSpec
    val fieldNames = ioSpec.allFieldNames

    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.dataSetId)

      // load a ML model
      mlModel <- regressionRepo.get(input.mlModelId)

      // get all the fields
      fields <- dsa.fieldStore.find(FieldIdentity.name #-> fieldNames)
      fieldNameSpecs = fields.map(field => (field.name, field.fieldTypeSpec)).toSeq

      // order field (and type)
      orderField <- dsa.fieldStore.get(ioSpec.orderFieldName).map(_.get)
      orderFieldType = ftf(orderField.fieldTypeSpec).asValueOf[Any]
      orderedValues = ioSpec.orderedStringValues.map(x => orderFieldType.displayStringToValue(x).get)

      // group id field (and type)
      groupIdField <- dsa.fieldStore.get(ioSpec.groupIdFieldName).map(_.get)
      groupIdFieldType = ftf(groupIdField.fieldTypeSpec).asValueOf[Any]

      // filter criterion
      filterCriterion <- loadCriterion(dsa, ioSpec.filterId)

      // not null field criteria
      notNullFieldCriteria = fields.map(field => NotEqualsNullCriterion(field.name)).toSeq

      // jsons
      data <- dsa.dataSetStore.find(
        criterion = And(filterCriterion +: notNullFieldCriteria),
        projection = fieldNames
      )

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val results = mlService.regressRowTemporalSeries(
          data,
          fieldNameSpecs,
          ioSpec.inputFieldNames,
          ioSpec.outputFieldName,
          ioSpec.orderFieldName,
          orderedValues,
          Some(ioSpec.groupIdFieldName),
          mlModel,
          input.learningSetting
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.foreach(exportResults)
  }

  private def loadCriterion(dsa: DataSetAccessor, filterId: Option[BSONObjectID]): Future[Criterion] =
    for {
      filter <- filterId match {
        case Some(filterId) => dsa.filterStore.get(filterId)
        case None => Future(None)
      }

      criteria <- filter match {
        case Some(filter) => FieldUtil.toCriterion(dsa.fieldStore, filter.conditions)
        case None => Future(And())
      }
    } yield
      criteria
}

case class RunRowTimeSeriesDLRegressionSpec(
  dataSetId: String,
  ioSpec: TemporalGroupIOSpec,
  mlModelId: BSONObjectID,
  learningSetting: TemporalRegressionLearningSetting
)