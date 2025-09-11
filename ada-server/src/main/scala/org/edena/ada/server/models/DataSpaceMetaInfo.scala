package org.edena.ada.server.models

import java.util.Date
import reactivemongo.api.bson.BSONObjectID
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer

case class DataSpaceMetaInfo(
  _id: Option[BSONObjectID],
  name: String,
  sortOrder: Int,
  timeCreated: Date = new Date(),
  dataSetMetaInfos: Seq[DataSetMetaInfo] = Nil,
  parentId: Option[BSONObjectID] = None,
  var children: Buffer[DataSpaceMetaInfo] = ListBuffer[DataSpaceMetaInfo]()
)

object DataSpaceMetaInfo {
  def fromPOJO(pojo: DataSpaceMetaInfoPOJO): DataSpaceMetaInfo = {
    val originalItem = Option(pojo.getOriginalItem)
    
    DataSpaceMetaInfo(
      _id = Option(pojo.get_id()).map(BSONObjectID.parse(_).get),
      name = pojo.getName,
      sortOrder = pojo.getSortOrder,
      timeCreated = pojo.getTimeCreated,
      parentId = Option(pojo.getParentId).map(BSONObjectID.parse(_).get),
      // take the rest from the original item
      dataSetMetaInfos = originalItem.map(_.dataSetMetaInfos).getOrElse(Nil),
      children = originalItem.map(_.children).getOrElse(ListBuffer[DataSpaceMetaInfo]())
    )
  }
  
  def toPOJO(dataSpaceMetaInfo: DataSpaceMetaInfo): DataSpaceMetaInfoPOJO = {
    val pojo = new DataSpaceMetaInfoPOJO()
    pojo.set_id(dataSpaceMetaInfo._id.map(_.stringify).orNull)
    pojo.setName(dataSpaceMetaInfo.name)
    pojo.setSortOrder(dataSpaceMetaInfo.sortOrder)
    pojo.setTimeCreated(dataSpaceMetaInfo.timeCreated)
    pojo.setParentId(dataSpaceMetaInfo.parentId.map(_.stringify).orNull)
    // Preserve original item for complex fields (dataSetMetaInfos, children)
    pojo.setOriginalItem(dataSpaceMetaInfo)
    pojo
  }
}