package org.edena.ada.server.models

import play.api.libs.functional.syntax._
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.models.DataSetFormattersAndIds.widgetSpecFormat

import java.util.Date
import org.edena.json.EitherFormat
import org.edena.store.json.BSONObjectIdentity
import org.edena.core.FilterCondition
import org.edena.ada.server.models.Filter._
import org.edena.core.store.{AscSort, DescSort, Sort}
import org.edena.json.{EnumFormat, RuntimeClassFormat, SubTypeFormat}
import play.api.libs.json._
import org.edena.store.json.BSONObjectIDFormat

case class DataView(
  _id: Option[BSONObjectID],
  name: String,
  filterOrIds: Seq[Either[Seq[FilterCondition], BSONObjectID]],
  tableColumnNames: Seq[String],
  initTableSortFieldName: Option[String] = None,
  initTableSortType: Option[SortType.Value] = None,
  widgetSpecs: Seq[WidgetSpec],
  elementGridWidth: Int = 3,
  default: Boolean = false,
  isPrivate: Boolean = false,
  generationMethod: WidgetGenerationMethod.Value = WidgetGenerationMethod.Auto,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date(),
  var createdBy: Option[User] = None
)

object WidgetGenerationMethod extends Enumeration {

  val Auto = Value("Auto")
  val FullData = Value("Full Data")
  val StreamedAll = Value("Streamed All")
  val StreamedIndividually = Value("Streamed Individually")
  val RepoAndFullData = Value("Repo and Full Data")
  val RepoAndStreamedAll = Value("Repo and Streamed All")
  val RepoAndStreamedIndividually = Value("Repo and Streamed Individually")

  implicit class ValueExt(method: WidgetGenerationMethod.Value) {
    def isRepoBased = method == RepoAndFullData || method == RepoAndStreamedAll || method == RepoAndStreamedIndividually
  }
}

object SortType extends Enumeration {
  val Asc, Desc = Value
}

object DataView {

  implicit val eitherFormat = EitherFormat[Seq[FilterCondition], BSONObjectID]
  implicit val generationMethodFormat = EnumFormat(WidgetGenerationMethod)
  implicit val sortTypeFormat = EnumFormat(SortType)

  implicit val dataViewFormat : Format[DataView] = (
    (__ \ "_id").formatNullable[BSONObjectID] and
    (__ \ "name").format[String] and
    (__ \ "filterOrIds").format[Seq[Either[Seq[FilterCondition], BSONObjectID]]] and
    (__ \ "tableColumnNames").format[Seq[String]] and
    (__ \ "initTableSortFieldName").formatNullable[String] and
    (__ \ "initTableSortType").formatNullable[SortType.Value] and
    (__ \ "widgetSpecs").format[Seq[WidgetSpec]] and
    (__ \ "elementGridWidth").format[Int] and
    (__ \ "default").format[Boolean] and
    (__ \ "isPrivate").format[Boolean] and
    (__ \ "generationMethod").format[WidgetGenerationMethod.Value] and
    (__ \ "createdById").formatNullable[BSONObjectID] and
    (__ \ "timeCreated").format[Date]
  )(
    DataView(_, _, _, _, _, _, _, _, _, _, _, _, _),
    (item: DataView) =>  (
      item._id,
      item.name,
      item.filterOrIds,
      item.tableColumnNames,
      item.initTableSortFieldName,
      item.initTableSortType,
      item.widgetSpecs,
      item.elementGridWidth,
      item.default,
      item.isPrivate,
      item.generationMethod,
      item.createdById,
      item.timeCreated
    )
  )

  implicit object DataViewIdentity extends BSONObjectIdentity[DataView] {
    def of(entity: DataView): Option[BSONObjectID] = entity._id
    protected def set(entity: DataView, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  def applyMain(
    tableColumnNames: Seq[String],
    distributionChartFieldNames: Seq[String],
    elementGridWidth: Int,
    generationMethod: WidgetGenerationMethod.Value
  ) =
    DataView(
      None,
      "Main",
      Nil,
      tableColumnNames,
      None,
      None,
      distributionChartFieldNames.map(DistributionWidgetSpec(_, None)),
      elementGridWidth,
      true,
      false,
      generationMethod
    )
}