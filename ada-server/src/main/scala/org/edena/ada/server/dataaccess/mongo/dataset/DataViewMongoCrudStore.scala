package org.edena.ada.server.dataaccess.mongo.dataset

import javax.inject.Inject
import com.google.inject.assistedinject.Assisted
import org.edena.ada.server.dataaccess.StoreTypes.DictionaryRootStore
import org.edena.ada.server.models.DataView
import org.edena.ada.server.models.DataView.{DataViewIdentity, dataViewFormat}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

import scala.concurrent.Future

class DataViewMongoCrudStore @Inject()(
  @Assisted dataSetId : String,
  dictionaryRepo: DictionaryRootStore
) extends DictionarySubordinateMongoCrudStore[DataView, BSONObjectID]("dataviews", dataSetId, dictionaryRepo) {

  override def save(entity: DataView): Future[BSONObjectID] = {
    val initializedId = DataViewIdentity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(DataViewIdentity.set(entity, initializedId))
  }
}