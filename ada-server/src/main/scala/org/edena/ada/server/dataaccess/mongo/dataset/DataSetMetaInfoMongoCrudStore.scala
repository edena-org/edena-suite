package org.edena.ada.server.dataaccess.mongo.dataset

import javax.inject.Inject
import com.google.inject.assistedinject.Assisted
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoMongoExtraStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models._
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.SubordinateObjectMongoCrudStore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataSetMetaInfoMongoCrudStore @Inject()(
    @Assisted dataSpaceId: BSONObjectID,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoMongoExtraStore
  ) extends SubordinateObjectMongoCrudStore[DataSetMetaInfo, BSONObjectID, DataSpaceMetaInfo, BSONObjectID]("dataSetMetaInfos", dataSpaceMetaInfoRepo) {

  rootId = Some(Future(dataSpaceId)) // initialize the data space id

  override protected def getDefaultRoot =
    DataSpaceMetaInfo(Some(dataSpaceId), "", 0, new java.util.Date(), Seq[DataSetMetaInfo]())

  override protected def getRootObject =
    Future(Some(getDefaultRoot))
  //    rootRepo.find(Seq(DataSpaceMetaInfoIdentity.name #== dataSpaceId)).map(_.headOption)

  override def save(entity: DataSetMetaInfo): Future[BSONObjectID] = {
    val identity = DataSetMetaInfoIdentity
    val initializedId = identity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(identity.set(entity, initializedId))
  }
}