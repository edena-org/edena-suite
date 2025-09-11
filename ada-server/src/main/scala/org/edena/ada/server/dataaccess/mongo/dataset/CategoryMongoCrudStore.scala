package org.edena.ada.server.dataaccess.mongo.dataset

import javax.inject.Inject
import com.google.inject.assistedinject.Assisted
import org.edena.ada.server.dataaccess.StoreTypes.DictionaryRootStore
import org.edena.ada.server.models.{Category, DataSetFormattersAndIds}
import DataSetFormattersAndIds.{CategoryIdentity, categoryFormat}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

import scala.concurrent.Future

class CategoryMongoCrudStore @Inject()(
  @Assisted dataSetId : String,
  dictionaryRepo: DictionaryRootStore
) extends DictionarySubordinateMongoCrudStore[Category, BSONObjectID](
  "categories", dataSetId, dictionaryRepo
) {

  override def save(entity: Category): Future[BSONObjectID] = {
    val initializedId = CategoryIdentity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(CategoryIdentity.set(entity, initializedId))
  }
}