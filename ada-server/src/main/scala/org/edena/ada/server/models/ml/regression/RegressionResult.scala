package org.edena.ada.server.models.ml.regression

import org.edena.store.json.BSONObjectIdentity
import org.edena.json._
import org.edena.spark_ml.models.VectorScalerType
import org.edena.ada.server.models.ml.regression.Regressor.eitherFormat
import org.edena.ada.server.models.ml.ReservoirSpec.reservoirSpecFormat
import org.edena.spark_ml.models.regression.RegressionEvalMetric
import org.edena.spark_ml.models.result._
import org.edena.spark_ml.models.setting._

import play.api.libs.json.{Json, _}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

object RegressionResult {

  implicit val regressionResultFormat: Format[RegressionResult] = {
    implicit val vectorScalerTypeFormat = EnumFormat(VectorScalerType)
    implicit val evalMetricFormat = EnumFormat(RegressionEvalMetric)
    createRegressionResultFormat(vectorScalerTypeFormat, evalMetricFormat)
  }

  implicit val standardRegressionResultFormat: Format[StandardRegressionResult] = {
    implicit val vectorScalerTypeFormat = EnumFormat(VectorScalerType)
    implicit val evalMetricFormat = EnumFormat(RegressionEvalMetric)
    createStandardRegressionResultFormat(vectorScalerTypeFormat, evalMetricFormat)
  }

  implicit val temporalRegressionResultFormat: Format[TemporalRegressionResult] = {
    implicit val vectorScalerTypeFormat = EnumFormat(VectorScalerType)
    implicit val evalMetricFormat = EnumFormat(RegressionEvalMetric)
    createTemporalRegressionResultFormat(vectorScalerTypeFormat, evalMetricFormat)
  }

  implicit val standardRegressionRunSpecFormat: Format[RegressionRunSpec] = {
    implicit val vectorScalerTypeFormat = EnumFormat(VectorScalerType)
    implicit val evalMetricFormat = EnumFormat(RegressionEvalMetric)
    createStandardRegressionRunSpecFormat(vectorScalerTypeFormat, evalMetricFormat)
  }

  implicit val temporalRegressionRunSpecFormat: Format[TemporalRegressionRunSpec] = {
    implicit val vectorScalerTypeFormat = EnumFormat(VectorScalerType)
    implicit val evalMetricFormat = EnumFormat(RegressionEvalMetric)
    createTemporalRegressionRunSpecFormat(vectorScalerTypeFormat, evalMetricFormat)
  }

  implicit object RegressionResultIdentity extends BSONObjectIdentity[RegressionResult] {
    def of(entity: RegressionResult): Option[BSONObjectID] = entity._id

    protected def set(entity: RegressionResult, id: Option[BSONObjectID]) =
      entity match {
        case x: StandardRegressionResult => x.copy(_id = id)
        case x: TemporalRegressionResult => x.copy(_id = id)
      }
  }


  // helper functions

  def createRegressionResultFormat(
    implicit vectorScalerTypeFormat: Format[VectorScalerType.Value],
    evalMetricFormat: Format[RegressionEvalMetric.Value]
  ): Format[RegressionResult] = {
    implicit val standardFormat = createStandardRegressionResultFormat(vectorScalerTypeFormat, evalMetricFormat)
    implicit val temporalFormat = createTemporalRegressionResultFormat(vectorScalerTypeFormat, evalMetricFormat)

    new SubTypeFormat[RegressionResult](
      Seq(
        RuntimeClassFormat(standardFormat),
        RuntimeClassFormat(temporalFormat)
      )
    )
  }

  def createStandardRegressionResultFormat(
    implicit vectorScalerTypeFormat: Format[VectorScalerType.Value],
    evalMetricFormat: Format[RegressionEvalMetric.Value]
  ) = {
    implicit val regressionRunSpecFormat = createStandardRegressionRunSpecFormat(vectorScalerTypeFormat, evalMetricFormat)
    implicit val regressionMetricStatsValuesFormat = Json.format[MetricStatsValues]
    implicit val regressionMetricStatsFormat = Json.format[RegressionMetricStats]

    new FlattenFormat(Json.format[StandardRegressionResult], "-", Set("_id", "filterId", "replicationFilterId", "mlModelId"))
  }

  def createTemporalRegressionResultFormat(
    implicit vectorScalerTypeFormat: Format[VectorScalerType.Value],
    evalMetricFormat: Format[RegressionEvalMetric.Value]
  ) = {
    implicit val regressionRunSpecFormat = createTemporalRegressionRunSpecFormat(vectorScalerTypeFormat, evalMetricFormat)
    implicit val regressionMetricStatsValuesFormat = Json.format[MetricStatsValues]
    implicit val regressionMetricStatsFormat = Json.format[RegressionMetricStats]

    new FlattenFormat(Json.format[TemporalRegressionResult], "-", Set("_id", "filterId", "replicationFilterId", "mlModelId"))
  }

  private def createStandardRegressionRunSpecFormat(
    implicit vectorScalerTypeFormat: Format[VectorScalerType.Value],
    evalMetricFormat: Format[RegressionEvalMetric.Value]
  ) = {
    implicit val tupleFormat = TupleFormat[String, Double]
    implicit val learningSettingFormat = Json.format[RegressionLearningSetting]
    implicit val ioSpecFormat = Json.format[IOSpec]

    Json.format[RegressionRunSpec]
  }

  private def createTemporalRegressionRunSpecFormat(
    implicit vectorScalerTypeFormat: Format[VectorScalerType.Value],
    evalMetricFormat: Format[RegressionEvalMetric.Value]
  ) = {
    implicit val tupleFormat = TupleFormat[String, Double]
    implicit val learningSettingFormat = Json.format[RegressionLearningSetting]
    implicit val intEitherFormat = eitherFormat[Int]
    implicit val temporalLearningSettingFormat = Json.format[TemporalRegressionLearningSetting]
    implicit val ioSpecFormat = Json.format[TemporalGroupIOSpec]

    Json.format[TemporalRegressionRunSpec]
  }
}
