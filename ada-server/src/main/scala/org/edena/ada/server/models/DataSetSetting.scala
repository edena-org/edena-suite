package org.edena.ada.server.models

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
  customStorageCollectionName: Option[String] = None
) {
  def this(
    dataSetId: String,
    storageType: StorageType.Value
  ) =
    this(_id = None, dataSetId = dataSetId, keyFieldName = "_id", storageType = storageType)

  def this(dataSetId: String) =
    this(dataSetId, StorageType.Mongo)
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

      extraNavigationItems = originalItem.map(_.extraNavigationItems).getOrElse(Nil),
      extraExportActions = originalItem.map(_.extraExportActions).getOrElse(Nil),
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
    // Preserve original item for complex fields
    pojo.setOriginalItem(dataSetSetting)
    pojo
  }
}