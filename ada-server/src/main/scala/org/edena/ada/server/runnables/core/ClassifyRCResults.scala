package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.core.store.Criterion._
import org.edena.ada.server.models.Filter
import org.edena.ada.server.AdaException
import org.edena.core.util.seqFutures
import org.edena.spark_ml.MLResultUtil
import org.edena.spark_ml.models.setting.{ClassificationLearningSetting, ClassificationRunSpec, IOSpec}
import org.edena.ada.server.dataaccess.StoreTypes.ClassifierStore
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.services.DataSetService
import org.edena.ada.server.services.ml.MachineLearningService
import org.edena.ada.server.services.StatsService
import org.edena.ada.server.field.FieldUtil
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.core.store.{And, Criterion}
import org.edena.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class ClassifyRCResults @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    statsService: StatsService,
    dataSetService: DataSetService,
    classificationRepo: ClassifierStore
  ) extends InputFutureRunnableExt[ClassifyRCResultsSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  private val dataSetFieldName = "inputOutputSpec-resultDataSetId"

  override def runAsFuture(input: ClassifyRCResultsSpec) =
    for {
      // get the data set ids
      dataSetIds <- dataSetIds(input)

      // clasify data sets one-by-one
      _ <- seqFutures(dataSetIds) { classify(_, input) }
    } yield
      ()

  private def dataSetIds(input: ClassifyRCResultsSpec) = {
    def resultsDataSetIds(dataSetId: String) =
      for {
        dsa <- dsaf.getOrError(dataSetId)

        jsons <- dsa.dataSetStore.find(projection = Seq(dataSetFieldName))
      } yield
        jsons.map { json =>
          (json \ dataSetFieldName).get.as[String]
        }.toSeq.sorted

    (
      input.rcWeightDataSetIdPrefix,
      input.rcWeightDataSetIdSuffixFrom,
      input.rcWeightDataSetIdSuffixTo
    ).zipped.headOption.map { case (dataSetIdPrefix, from, to) =>
      Future((from to to).map(dataSetIdPrefix + _).sorted)
    }.getOrElse(
      resultsDataSetIds(input.resultsDataSetId.getOrElse(
        throw new AdaException("Results data set id or RC weight data set id (with suffix from-to) expected.")
      ))
    )
  }

  private def classify(dataSetId: String, spec: ClassifyRCResultsSpec): Future[Unit] = {
    logger.info(s"Classifying RC weight data set $dataSetId.")

    dsaf.getOrError(dataSetId).flatMap(classifyAux(spec))
  }

  private def classifyAux(
    spec: ClassifyRCResultsSpec)(
    dsa: DataSetAccessor
  ): Future[Unit] = {
    val mlModelFuture = classificationRepo.get(spec.mlModelId)

    val filterFuture = spec.filterName match {
      case Some(filterName) =>
        dsa.filterStore.find("name" #== Some(filterName)).map(_.headOption)
      case None =>
        Future(None)
    }

    val allFieldsFuture = dsa.fieldStore.find()

    for {
      // get a classification ml model
      mlModel <- mlModelFuture

      // get a filter (if any)
      filter <- filterFuture

      // get all the fields
      allFields <- allFieldsFuture

      // filter the weight fields
      weightsFieldNames = allFields.filter(_.name.startsWith("rc_w_")).map(_.name).toSeq

      // prepare filter criterion
      criterion <- loadCriterion(dsa, filter)

      // load the fields
      fields <- dsa.fieldStore.find(FieldIdentity.name #-> (weightsFieldNames ++ Seq(spec.outputFieldName)))

      // IO spec
      ioSpec = IOSpec(
        weightsFieldNames,
        spec.outputFieldName,
        filter.map(_._id.get),
        spec.replicationFilterId
      )

      // select fields
      selectedFields <-
        spec.learningSetting.featuresSelectionNum.map { featuresSelectionNum =>
          val inputFields = fields.filter(!_.name.equals(ioSpec.outputFieldName)).toSeq
          val outputField = fields.find(_.name.equals(ioSpec.outputFieldName)).get
          statsService.selectFeaturesAsAnovaChiSquare(dsa.dataSetStore, criterion, inputFields, outputField, featuresSelectionNum).map { selectedInputFields =>
            selectedInputFields ++ Seq(outputField)
          }
        }.getOrElse(
          Future(fields)
        )

      // classify and save the result
      _ <- mlModel match {
        case Some(mlModel) =>
          val fieldNameAndSpecs = selectedFields.toSeq.map(field => (field.name, field.fieldTypeSpec))
          val runSpec = ClassificationRunSpec(ioSpec, spec.mlModelId, spec.learningSetting)

          for {
            // jsons
            jsons <- dsa.dataSetStore.find(criterion, projection = selectedFields.map(_.name))

            // results holder
            resultsHolder <- mlService.classifyStatic(jsons, fieldNameAndSpecs, spec.outputFieldName, mlModel, runSpec.learningSetting)

            // final results
            finalResult = MLResultUtil.createStandardClassificationResult(runSpec, MLResultUtil.calcMetricStats(resultsHolder.performanceResults), Nil)

            // save results
            _ <- dsa.classificationResultStore.save(finalResult)
          } yield
            ()

        case None => Future(())
      }
    } yield
      ()
  }

  private def loadCriterion(dsa: DataSetAccessor, filter: Option[Filter]): Future[Criterion] =
    filter match {
      case Some(filter) => FieldUtil.toCriterion(dsa.fieldStore, filter.conditions)
      case None => Future(And()) // no-op
    }
}

case class ClassifyRCResultsSpec(
  resultsDataSetId: Option[String],
  rcWeightDataSetIdPrefix: Option[String],
  rcWeightDataSetIdSuffixFrom: Option[Int],
  rcWeightDataSetIdSuffixTo: Option[Int],
  mlModelId: BSONObjectID,
  outputFieldName: String,
  filterName: Option[String],
  replicationFilterId: Option[BSONObjectID],
  learningSetting: ClassificationLearningSetting
)