package org.edena.ada.server.models

import java.util.Date

import reactivemongo.api.bson.BSONObjectID

case class DataSetMetaInfo(
  _id: Option[BSONObjectID] = None,
  id: String,
  name: String,
  description: Option[String] = None,
  sortOrder: Int = 0,
  hide: Boolean = false,
  dataSpaceId: BSONObjectID,
  timeCreated: Date = new Date(),
  sourceDataSetId: Option[BSONObjectID] = None
)

object DataSetMetaInfo {
  def fromPOJO(pojo: DataSetMetaInfoPOJO): DataSetMetaInfo = {
    DataSetMetaInfo(
      _id = Option(pojo.get_id()).map(BSONObjectID.parse(_).get),
      id = pojo.getId,
      name = pojo.getName,
      description = Option(pojo.getDescription),
      sortOrder = Option(pojo.getSortOrder).map(_.intValue()).getOrElse(0),
      hide = Option(pojo.getHide).exists(_.booleanValue()),
      dataSpaceId = BSONObjectID.parse(pojo.getDataSpaceId).get,
      timeCreated = Option(pojo.getTimeCreated).getOrElse(new Date()),
      sourceDataSetId = Option(pojo.getSourceDataSetId).map(BSONObjectID.parse(_).get)
    )
  }
  
  def toPOJO(dataSetMetaInfo: DataSetMetaInfo): DataSetMetaInfoPOJO = {
    val pojo = new DataSetMetaInfoPOJO()
    pojo.set_id(dataSetMetaInfo._id.map(_.stringify).orNull)
    pojo.setId(dataSetMetaInfo.id)
    pojo.setName(dataSetMetaInfo.name)
    pojo.setDescription(dataSetMetaInfo.description.orNull)
    pojo.setSortOrder(dataSetMetaInfo.sortOrder)
    pojo.setHide(dataSetMetaInfo.hide)
    pojo.setDataSpaceId(dataSetMetaInfo.dataSpaceId.stringify)
    pojo.setTimeCreated(dataSetMetaInfo.timeCreated)
    pojo.setSourceDataSetId(dataSetMetaInfo.sourceDataSetId.map(_.stringify).orNull)
    pojo
  }
}
