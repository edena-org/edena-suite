package org.edena.ada.server.models.datatrans

import java.util.Date

import org.edena.store.json.BSONObjectIdentity
import org.edena.json._
import org.edena.ada.server.models.{Schedulable, ScheduledTime, StorageType, WeekDay}
import org.edena.ada.server.services.StaticLookupCentralImpl
import org.edena.core.store.StreamSpec
import play.api.libs.json.{Format, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.core.DefaultTypes.Seq

trait DataSetMetaTransformation extends Schedulable {
  val _id: Option[BSONObjectID]
  val timeCreated: Date
  val timeLastExecuted: Option[Date]

  val sourceDataSetIds: Seq[String]

  def copyCore(
    _id: Option[BSONObjectID],
    timeCreated: Date,
    timeLastExecuted: Option[Date],
    scheduled: Boolean,
    scheduledTime: Option[ScheduledTime]
  ): DataSetMetaTransformation
}

trait DataSetTransformation extends DataSetMetaTransformation {
  val streamSpec: StreamSpec
  val resultDataSetSpec: ResultDataSetSpec
  val resultDataSetId = resultDataSetSpec.id
  val resultDataSetName = resultDataSetSpec.name
  val resultStorageType = resultDataSetSpec.storageType
}

case class ResultDataSetSpec(
  id: String,
  name: String,
  storageType: StorageType.Value = StorageType.ElasticSearch
)

object DataSetTransformation {

  implicit val weekDayFormat = EnumFormat(WeekDay)
  implicit val scheduleTimeFormat = Json.format[ScheduledTime]
  implicit val storageTypeFormat = EnumFormat(StorageType)
  implicit val resultDataSetSpecFormat = Json.format[ResultDataSetSpec]
  implicit val streamSpecFormat = Json.format[StreamSpec]
  implicit val tupleFormat = TupleFormat[String, String]
  implicit val tupleFormat2 = TupleFormat[String, String, String]
  implicit val optionIntFormat = new OptionFormat[Int]
  implicit val tupleFormat3 = TupleFormat[String, Option[Int]]

  private val subFormats = new StaticLookupCentralImpl[HasFormat[DataSetMetaTransformation]](
    getClass.getPackage.getName, true
  ).apply.map(x => RuntimeClassFormat(x.format, x.runtimeClass))

  implicit val dataSetMetaTransformationFormat: Format[DataSetMetaTransformation] = new SubTypeFormat[DataSetMetaTransformation](subFormats)

  implicit object DataSetMetaTransformationIdentity extends BSONObjectIdentity[DataSetMetaTransformation] {
    def of(entity: DataSetMetaTransformation): Option[BSONObjectID] = entity._id

    protected def set(entity: DataSetMetaTransformation, id: Option[BSONObjectID]) =
      entity.copyCore(id, entity.timeCreated, entity.timeLastExecuted, entity.scheduled, entity.scheduledTime)
  }
}