package org.edena.ada.server.dataaccess.mongo.dataset

import javax.inject.Inject
import com.google.inject.assistedinject.Assisted
import org.edena.ada.server.dataaccess.StoreTypes.DictionaryRootStore
import org.edena.ada.server.models.Filter
import org.edena.ada.server.models.Filter.{FilterIdentity, filterFormat}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

import scala.concurrent.Future

class FilterMongoCrudStore @Inject()(
  @Assisted dataSetId : String,
  dictionaryRepo: DictionaryRootStore
) extends DictionarySubordinateMongoCrudStore[Filter, BSONObjectID]("filters", dataSetId, dictionaryRepo) {

  override def save(entity: Filter): Future[BSONObjectID] = {
    val initializedId = FilterIdentity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(FilterIdentity.set(entity, initializedId))
  }
}