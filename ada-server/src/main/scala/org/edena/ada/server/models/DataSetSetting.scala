package org.edena.ada.server.models

import play.api.libs.json.{Format, Json}
import reactivemongo.api.bson.BSONObjectID

case class DataSetSetting(
  _id: Option[BSONObjectID],
  dataSetId: String,
  keyFieldName: String,
  exportOrderByFieldName: Option[String] = None,
  defaultScatterXFieldName: Option[String] = None,
  defaultScatterYFieldName: Option[String] = None,
  defaultDistributionFieldName: Option[String] = None,
  defaultCumulativeCountFieldName: Option[String] = None,
  filterShowFieldStyle: Option[FilterShowFieldStyle.Value] = None,
  filterShowNonNullCount: Boolean = false,
  displayItemName: Option[String] = None,
  storageType: StorageType.Value,
  mongoAutoCreateIndexForProjection: Boolean = false,
  cacheDataSet: Boolean = false,
  ownerId: Option[BSONObjectID] = None,
  showSideCategoricalTree: Boolean = true,
  extraNavigationItems: Seq[NavigationItem] = Nil,
  extraExportActions: Seq[Link] = Nil,
  customControllerClassName: Option[String] = None,
  description: Option[String] = None,
  widgetEngineClassName: Option[String] = None,
  customStorageCollectionName: Option[String] = None,
  getListItemURL: Option[String] = None,
  recordView: Option[RecordViewSetting] = None
) {
  def this(
    dataSetId: String,
    storageType: StorageType.Value
  ) =
    this(_id = None, dataSetId = dataSetId, keyFieldName = "_id", storageType = storageType)

  def this(dataSetId: String) =
    this(dataSetId, StorageType.Mongo)
}

/**
  * How the page of a data set's record (item) looks. An option not set takes its default.
  *
  * @param title the page's title, `{{FIELD_NAME}}` replaced by the record's value of the field; default: the item name
  * @param summaryFieldNames the fields shown at the top, in this order; default: the table columns of the data view the
  *                          record was opened from (or of the default view)
  * @param sectionOrder the names of the categories shown first, in this order (a category's subcategories with it); the
  *                     others follow in the category tree's order
  * @param collapsedSections the names of the categories shown collapsed at first
  * @param hiddenFieldNames the fields never shown on the page
  */
case class RecordViewSetting(
  title: Option[String] = None,
  summaryFieldNames: Seq[String] = Nil,
  sectionOrder: Seq[String] = Nil,
  collapsedSections: Seq[String] = Nil,
  hiddenFieldNames: Seq[String] = Nil
) {
  def isEmpty: Boolean =
    title.isEmpty && summaryFieldNames.isEmpty && sectionOrder.isEmpty && collapsedSections.isEmpty && hiddenFieldNames.isEmpty
}

object RecordViewSetting {
  // a missing option takes its default
  implicit val format: Format[RecordViewSetting] = Json.using[Json.WithDefaultValues].format[RecordViewSetting]
}

object StorageType extends Enumeration {
  val Mongo = Value("Mongo")
  val ElasticSearch = Value("Elastic Search")
}

object DataSetSetting {
  def fromPOJO(pojo: DataSetSettingPOJO): DataSetSetting = {
    val originalItem = Option(pojo.getOriginalItem)
    
    DataSetSetting(
      _id = Option(pojo.get_id()).map(BSONObjectID.parse(_).get),
      dataSetId = pojo.getDataSetId,
      keyFieldName = pojo.getKeyFieldName,
      exportOrderByFieldName = Option(pojo.getExportOrderByFieldName),
      defaultScatterXFieldName = Option(pojo.getDefaultScatterXFieldName),
      defaultScatterYFieldName = Option(pojo.getDefaultScatterYFieldName),
      defaultDistributionFieldName = Option(pojo.getDefaultDistributionFieldName),
      defaultCumulativeCountFieldName = Option(pojo.getDefaultCumulativeCountFieldName),
      filterShowFieldStyle = Option(pojo.getFilterShowFieldStyle).flatMap(s => FilterShowFieldStyle.values.find(_.toString == s)),
      filterShowNonNullCount = Option(pojo.getFilterShowNonNullCount).map(_.booleanValue()).getOrElse(false),
      displayItemName = Option(pojo.getDisplayItemName),
      storageType = StorageType.values.find(_.toString == pojo.getStorageType).getOrElse(StorageType.Mongo),
      mongoAutoCreateIndexForProjection = Option(pojo.getMongoAutoCreateIndexForProjection).map(_.booleanValue()).getOrElse(false),
      cacheDataSet = Option(pojo.getCacheDataSet).map(_.booleanValue()).getOrElse(false),
      ownerId = Option(pojo.getOwnerId).map(BSONObjectID.parse(_).get),
      showSideCategoricalTree = Option(pojo.getShowSideCategoricalTree).map(_.booleanValue()).getOrElse(true),
      customControllerClassName = Option(pojo.getCustomControllerClassName),
      description = Option(pojo.getDescription),
      widgetEngineClassName = Option(pojo.getWidgetEngineClassName),
      customStorageCollectionName = Option(pojo.getCustomStorageCollectionName),
      getListItemURL = Option(pojo.getGetListItemURL),

      extraNavigationItems = originalItem.map(_.extraNavigationItems).getOrElse(Nil),
      extraExportActions = originalItem.map(_.extraExportActions).getOrElse(Nil),
      // a field without a POJO counterpart must come from the original item, otherwise the (write-through) cache drops it
      recordView = originalItem.flatMap(_.recordView)
    )
  }
  
  def toPOJO(dataSetSetting: DataSetSetting): DataSetSettingPOJO = {
    val pojo = new DataSetSettingPOJO()
    pojo.set_id(dataSetSetting._id.map(_.stringify).orNull)
    pojo.setDataSetId(dataSetSetting.dataSetId)
    pojo.setKeyFieldName(dataSetSetting.keyFieldName)
    pojo.setExportOrderByFieldName(dataSetSetting.exportOrderByFieldName.orNull)
    pojo.setDefaultScatterXFieldName(dataSetSetting.defaultScatterXFieldName.orNull)
    pojo.setDefaultScatterYFieldName(dataSetSetting.defaultScatterYFieldName.orNull)
    pojo.setDefaultDistributionFieldName(dataSetSetting.defaultDistributionFieldName.orNull)
    pojo.setDefaultCumulativeCountFieldName(dataSetSetting.defaultCumulativeCountFieldName.orNull)
    pojo.setFilterShowFieldStyle(dataSetSetting.filterShowFieldStyle.map(_.toString).orNull)
    pojo.setFilterShowNonNullCount(dataSetSetting.filterShowNonNullCount)
    pojo.setDisplayItemName(dataSetSetting.displayItemName.orNull)
    pojo.setStorageType(dataSetSetting.storageType.toString)
    pojo.setMongoAutoCreateIndexForProjection(dataSetSetting.mongoAutoCreateIndexForProjection)
    pojo.setCacheDataSet(dataSetSetting.cacheDataSet)
    pojo.setOwnerId(dataSetSetting.ownerId.map(_.stringify).orNull)
    pojo.setShowSideCategoricalTree(dataSetSetting.showSideCategoricalTree)
    pojo.setCustomControllerClassName(dataSetSetting.customControllerClassName.orNull)
    pojo.setDescription(dataSetSetting.description.orNull)
    pojo.setWidgetEngineClassName(dataSetSetting.widgetEngineClassName.orNull)
    pojo.setCustomStorageCollectionName(dataSetSetting.customStorageCollectionName.orNull)
    pojo.setGetListItemURL(dataSetSetting.getListItemURL.orNull)
    // Preserve original item for complex fields
    pojo.setOriginalItem(dataSetSetting)
    pojo
  }
}