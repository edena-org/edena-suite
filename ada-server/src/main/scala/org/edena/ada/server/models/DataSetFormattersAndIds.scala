package org.edena.ada.server.models

import java.util.UUID
import org.edena.store.json.BSONObjectIdentity
import org.edena.json.{EnumFormat, RuntimeClassFormat, SerializableFormat, SubTypeFormat}
import org.edena.core.Identity
import play.api.libs.functional.syntax._
import org.edena.store.json.BSONObjectIDFormat
import play.api.libs.json._
import play.api.libs.json.{Format, JsObject, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.models.NavigationItem.navigationItemFormat
import org.edena.core.field.FieldTypeId

// JSON converters and identities

object DataSetFormattersAndIds {

  implicit val enumTypeFormat = EnumFormat(FieldTypeId)

  implicit val categoryFormat: Format[Category] = (
    (__ \ "_id").formatNullable[BSONObjectID] and
    (__ \ "name").format[String] and
    (__ \ "label").formatNullable[String] and
    (__ \ "parentId").formatNullable[BSONObjectID]
  )(Category(_, _, _, _), (item: Category) =>  (item._id, item.name, item.label, item.parentId))

  implicit val displayURLEnumTypeFormat = EnumFormat(URLType)

  implicit val fieldFormat: Format[Field] = (
    (__ \ "name").format[String] and
    (__ \ "label").formatNullable[String] and
    (__ \ "fieldType").format[FieldTypeId.Value] and
    (__ \ "isArray").format[Boolean] and
    (__ \ "enumValues").format[Map[String, String]] and
    (__ \ "displayDecimalPlaces").formatNullable[Int] and
    (__ \ "displayTrueValue").formatNullable[String] and
    (__ \ "displayFalseValue").formatNullable[String] and
    (__ \ "displayAsURLType").formatNullable[URLType.Value] and
    (__ \ "aliases").format[Seq[String]] and
    (__ \ "categoryId").formatNullable[BSONObjectID]
  )(
    Field(_, _, _, _, _, _, _, _, _, _, _),
    (item: Field) =>  (
      item.name, item.label, item.fieldType, item.isArray, item.enumValues, item.displayDecimalPlaces,
      item.displayTrueValue, item.displayFalseValue, item.displayAsURLType, item.aliases, item.categoryId
    )
  )

  implicit val chartTypeFormat = EnumFormat(ChartType)
  implicit val aggTypeFormat = EnumFormat(AggType)
  implicit val correlationTypeFormat = EnumFormat(CorrelationType)
  implicit val basicDisplayOptionsFormat = Json.format[BasicDisplayOptions]
  implicit val distributionDisplayOptionsFormat = Json.format[MultiChartDisplayOptions]

  // register your widget spec class here
  private val widgetSpecManifestedFormats: Seq[RuntimeClassFormat[_ <: WidgetSpec]] =
    Seq(
      RuntimeClassFormat(Json.format[DistributionWidgetSpec]),
      RuntimeClassFormat(Json.format[CumulativeCountWidgetSpec]),
      RuntimeClassFormat(Json.format[BoxWidgetSpec]),
      RuntimeClassFormat(Json.format[ScatterWidgetSpec]),
      RuntimeClassFormat(Json.format[ValueScatterWidgetSpec]),
      RuntimeClassFormat(Json.format[HeatmapAggWidgetSpec]),
      RuntimeClassFormat(Json.format[GridDistributionCountWidgetSpec]),
      RuntimeClassFormat(Json.format[CorrelationWidgetSpec]),
      RuntimeClassFormat(Json.format[IndependenceTestWidgetSpec]),
      RuntimeClassFormat(Json.format[BasicStatsWidgetSpec]),
      RuntimeClassFormat(Json.format[CustomHtmlWidgetSpec]),
      RuntimeClassFormat(Json.format[CategoricalCheckboxWidgetSpec]),
      RuntimeClassFormat(Json.format[XLineWidgetSpec])
    )

  val widgetSpecClasses: Seq[Class[_ <: WidgetSpec]] = widgetSpecManifestedFormats.map(_.runtimeClass)

  implicit val widgetSpecFormat: Format[WidgetSpec] = new SubTypeFormat[WidgetSpec](widgetSpecManifestedFormats)

  implicit val dictionaryFormat = Json.format[Dictionary]
  implicit val dataSetMetaInfoFormat = Json.format[DataSetMetaInfo]

  val dataSpaceMetaInfoFormat: Format[DataSpaceMetaInfo] = (
    (__ \ "_id").formatNullable[BSONObjectID] and
    (__ \ "name").format[String] and
    (__ \ "sortOrder").format[Int] and
    (__ \ "timeCreated").format[java.util.Date] and
    (__ \ "dataSetMetaInfos").format[Seq[DataSetMetaInfo]] and
    (__ \ "parentId").formatNullable[BSONObjectID]
  )(
    DataSpaceMetaInfo(_, _, _, _, _, _),
    (item: DataSpaceMetaInfo) =>  (
      item._id, item.name, item.sortOrder, item.timeCreated, item.dataSetMetaInfos, item.parentId
    )
  )
  implicit val serializableDataSpaceMetaInfoFormat: Format[DataSpaceMetaInfo] = new SerializableFormat(dataSpaceMetaInfoFormat.reads, dataSpaceMetaInfoFormat.writes)

  implicit val filterShowFieldStyleFormat = EnumFormat(FilterShowFieldStyle)
  implicit val storageTypeFormat = EnumFormat(StorageType)

  // Manual format needed because DataSetSetting has 23 fields (exceeds Play JSON's 22-field macro limit)
  val dataSetSettingFormat: OFormat[DataSetSetting] = OFormat(
    Reads[DataSetSetting] { json =>
      for {
        _id <- (json \ "_id").validateOpt[BSONObjectID]
        dataSetId <- (json \ "dataSetId").validate[String]
        keyFieldName <- (json \ "keyFieldName").validate[String]
        exportOrderByFieldName <- (json \ "exportOrderByFieldName").validateOpt[String]
        defaultScatterXFieldName <- (json \ "defaultScatterXFieldName").validateOpt[String]
        defaultScatterYFieldName <- (json \ "defaultScatterYFieldName").validateOpt[String]
        defaultDistributionFieldName <- (json \ "defaultDistributionFieldName").validateOpt[String]
        defaultCumulativeCountFieldName <- (json \ "defaultCumulativeCountFieldName").validateOpt[String]
        filterShowFieldStyle <- (json \ "filterShowFieldStyle").validateOpt[FilterShowFieldStyle.Value]
        filterShowNonNullCount <- (json \ "filterShowNonNullCount").validateOpt[Boolean].map(_.getOrElse(false))
        displayItemName <- (json \ "displayItemName").validateOpt[String]
        storageType <- (json \ "storageType").validate[StorageType.Value]
        mongoAutoCreateIndexForProjection <- (json \ "mongoAutoCreateIndexForProjection").validateOpt[Boolean].map(_.getOrElse(false))
        cacheDataSet <- (json \ "cacheDataSet").validateOpt[Boolean].map(_.getOrElse(false))
        ownerId <- (json \ "ownerId").validateOpt[BSONObjectID]
        showSideCategoricalTree <- (json \ "showSideCategoricalTree").validateOpt[Boolean].map(_.getOrElse(true))
        extraNavigationItems <- (json \ "extraNavigationItems").validateOpt[Seq[NavigationItem]].map(_.getOrElse(Nil))
        extraExportActions <- (json \ "extraExportActions").validateOpt[Seq[Link]].map(_.getOrElse(Nil))
        customControllerClassName <- (json \ "customControllerClassName").validateOpt[String]
        description <- (json \ "description").validateOpt[String]
        widgetEngineClassName <- (json \ "widgetEngineClassName").validateOpt[String]
        customStorageCollectionName <- (json \ "customStorageCollectionName").validateOpt[String]
        getListItemURL <- (json \ "getListItemURL").validateOpt[String]
      } yield DataSetSetting(
        _id, dataSetId, keyFieldName, exportOrderByFieldName, defaultScatterXFieldName,
        defaultScatterYFieldName, defaultDistributionFieldName, defaultCumulativeCountFieldName,
        filterShowFieldStyle, filterShowNonNullCount, displayItemName, storageType,
        mongoAutoCreateIndexForProjection, cacheDataSet, ownerId, showSideCategoricalTree,
        extraNavigationItems, extraExportActions, customControllerClassName, description,
        widgetEngineClassName, customStorageCollectionName, getListItemURL
      )
    },
    OWrites[DataSetSetting] { s =>
      Json.obj(
        "_id" -> s._id,
        "dataSetId" -> s.dataSetId,
        "keyFieldName" -> s.keyFieldName,
        "exportOrderByFieldName" -> s.exportOrderByFieldName,
        "defaultScatterXFieldName" -> s.defaultScatterXFieldName,
        "defaultScatterYFieldName" -> s.defaultScatterYFieldName,
        "defaultDistributionFieldName" -> s.defaultDistributionFieldName,
        "defaultCumulativeCountFieldName" -> s.defaultCumulativeCountFieldName,
        "filterShowFieldStyle" -> s.filterShowFieldStyle,
        "filterShowNonNullCount" -> s.filterShowNonNullCount,
        "displayItemName" -> s.displayItemName,
        "storageType" -> s.storageType,
        "mongoAutoCreateIndexForProjection" -> s.mongoAutoCreateIndexForProjection,
        "cacheDataSet" -> s.cacheDataSet,
        "ownerId" -> s.ownerId,
        "showSideCategoricalTree" -> s.showSideCategoricalTree,
        "extraNavigationItems" -> s.extraNavigationItems,
        "extraExportActions" -> s.extraExportActions,
        "customControllerClassName" -> s.customControllerClassName,
        "description" -> s.description,
        "widgetEngineClassName" -> s.widgetEngineClassName,
        "customStorageCollectionName" -> s.customStorageCollectionName,
        "getListItemURL" -> s.getListItemURL
      )
    }
  )
  implicit val serializableDataSetSettingFormat = new SerializableFormat(dataSetSettingFormat.reads, dataSetSettingFormat.writes)

  val serializableBSONObjectIDFormat = new SerializableFormat(BSONObjectIDFormat.reads, BSONObjectIDFormat.writes)

  implicit object DictionaryIdentity extends BSONObjectIdentity[Dictionary] {
    def of(entity: Dictionary): Option[BSONObjectID] = entity._id
    protected def set(entity: Dictionary, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object FieldIdentity extends Identity[Field, String] {
    override val name = "name"
    override def next = UUID.randomUUID().toString
    override def set(entity: Field, name: Option[String]): Field = entity.copy(name = name.getOrElse(""))
    override def of(entity: Field): Option[String] = Some(entity.name)
  }

  implicit object CategoryIdentity extends BSONObjectIdentity[Category] {
    def of(entity: Category): Option[BSONObjectID] = entity._id
    protected def set(entity: Category, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object DataSetMetaInfoIdentity extends BSONObjectIdentity[DataSetMetaInfo] {
    def of(entity: DataSetMetaInfo): Option[BSONObjectID] = entity._id
    protected def set(entity: DataSetMetaInfo, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object DataSpaceMetaInfoIdentity extends BSONObjectIdentity[DataSpaceMetaInfo] {
    def of(entity: DataSpaceMetaInfo): Option[BSONObjectID] = entity._id
    protected def set(entity: DataSpaceMetaInfo, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object DataSetSettingIdentity extends BSONObjectIdentity[DataSetSetting] {
    def of(entity: DataSetSetting): Option[BSONObjectID] = entity._id
    protected def set(entity: DataSetSetting, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}