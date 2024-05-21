package org.edena.ada.web.controllers

import org.edena.ada.server.models.Filter._
import org.edena.ada.web.controllers.FilterConditionExtraFormats.eitherFilterOrIdFormat
import org.edena.ada.server.models.{AggType, CorrelationType, Filter}
import org.edena.ada.server.models.ml._
import org.edena.ada.server.models.DataSetFormattersAndIds.enumTypeFormat
import org.edena.ada.server.models.ml.classification.ClassificationResult.{standardClassificationRunSpecFormat, temporalClassificationRunSpecFormat}
import org.edena.ada.server.models.ml.regression.RegressionResult.{standardRegressionRunSpecFormat, temporalRegressionRunSpecFormat}
import org.edena.core.FilterCondition
import org.edena.core.field.FieldTypeId
import org.edena.play.PageOrder
import org.edena.play.formatters.{EnumStringBindable, JsonQueryStringBindable}
import org.edena.spark_ml.models.classification.ClassificationEvalMetric
import org.edena.spark_ml.models.regression.RegressionEvalMetric
import org.edena.spark_ml.models.setting._
import org.edena.spark_ml.models.VectorScalerType

object QueryStringBinders {

  implicit val filterConditionQueryStringBinder = new JsonQueryStringBindable[Seq[FilterCondition]]
  implicit val filterQueryStringBinder = new JsonQueryStringBindable[Filter]
  implicit val fieldTypeIdsQueryStringBinder = new JsonQueryStringBindable[Seq[FieldTypeId.Value]]
  implicit val BSONObjectIDQueryStringBinder = BSONObjectIDQueryStringBindable
  implicit val filterOrIdBinder = new JsonQueryStringBindable[FilterOrId]
  implicit val filterOrIdSeqBinder = new JsonQueryStringBindable[Seq[FilterOrId]]
  implicit val tablePageSeqBinder = new JsonQueryStringBindable[Seq[PageOrder]]

  implicit val classificationRunSpecBinder = new JsonQueryStringBindable[ClassificationRunSpec]
  implicit val temporalClassificationRunSpecBinder = new JsonQueryStringBindable[TemporalClassificationRunSpec]
  implicit val regressionRunSpecBinder = new JsonQueryStringBindable[RegressionRunSpec]
  implicit val temporalRegressionRunSpecBinder = new JsonQueryStringBindable[TemporalRegressionRunSpec]

  implicit val vectorScalerTypeQueryStringBinder = new EnumStringBindable(VectorScalerType)
  implicit val classificationEvalMetricQueryStringBinder = new EnumStringBindable(ClassificationEvalMetric)
  implicit val regressionEvalMetricQueryStringBinder = new EnumStringBindable(RegressionEvalMetric)
  implicit val aggTypeQueryStringBinder = new EnumStringBindable(AggType)
  implicit val correlationTypeStringBinder = new EnumStringBindable(CorrelationType)
}