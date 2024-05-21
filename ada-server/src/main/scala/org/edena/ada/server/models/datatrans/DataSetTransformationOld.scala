package org.edena.ada.server.models.datatrans

import org.edena.core.store.StreamSpec
import org.edena.json.EnumFormat
import org.edena.ada.server.models.{ScheduledTime, StorageType}
import org.edena.spark_ml.models.VectorScalerType
import play.api.libs.json.Json

// TODO: migrate to DataSetTransformation
@Deprecated
trait DataSetTransformation2 {
  val resultDataSetSpec: ResultDataSetSpec
  def resultDataSetId = resultDataSetSpec.id
  def resultDataSetName = resultDataSetSpec.name
  def resultStorageType = resultDataSetSpec.storageType
}

case class SeriesProcessingSpec(
  fieldPath: String,
  processingType: SeriesProcessingType.Value,
  pastValuesCount: Int,
  addInitPaddingWithZeroes: Boolean = true
) {

  override def toString =
    if (pastValuesCount == 1)
      fieldPath + "_" + processingType.toString
    else
      fieldPath + "_" + processingType.toString + "-" + pastValuesCount.toString
}

case class DataSetSeriesProcessingSpec(
  sourceDataSetId: String,
  resultDataSetSpec: ResultDataSetSpec,
  seriesProcessingSpecs: Seq[SeriesProcessingSpec],
  preserveFieldNames: Seq[String],
  processingBatchSize: Option[Int],
  saveBatchSize: Option[Int]
) extends DataSetTransformation2


// TODO: This should be merged with DataSetSeriesProcessingSpec
case class DataSetSeriesTransformationSpec(
  sourceDataSetId: String,
  resultDataSetSpec: ResultDataSetSpec,
  seriesTransformationSpecs: Seq[SeriesTransformationSpec],
  preserveFieldNames: Seq[String],
  processingBatchSize: Option[Int],
  saveBatchSize: Option[Int]
) extends DataSetTransformation2

case class SeriesTransformationSpec(
  fieldPath: String,
  transformType: VectorScalerType.Value
) {
  override def toString =
    fieldPath + "_" + transformType.toString
}

case class SelfLinkSpec(
  dataSetId: String,
  keyFieldNames: Seq[String],
  valueFieldName: String,
  processingBatchSize: Option[Int],
  resultDataSetSpec: ResultDataSetSpec
) extends DataSetTransformation2

object SeriesProcessingType extends Enumeration {
  val Diff, RelativeDiff, Ratio, LogRatio, Min, Max, Mean = Value
}

object DataSetTransformation2 {
  implicit val storageTypeFormat = EnumFormat(StorageType)
  implicit val coreFormat = Json.format[ResultDataSetSpec]
  implicit val seriesProcessingTypeFormat = EnumFormat(SeriesProcessingType)
  implicit val seriesProcessingSpecFormat = Json.format[SeriesProcessingSpec]
  implicit val vectorTransformTypeFormat = EnumFormat(VectorScalerType)
  implicit val seriesTransformationSpecFormat = Json.format[SeriesTransformationSpec]
}