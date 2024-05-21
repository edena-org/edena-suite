package org.edena.ada.server.dataaccess.mongo.dataset

import com.google.inject.assistedinject.Assisted
import org.edena.ada.server.dataaccess.StoreTypes.DictionaryRootStore
import org.edena.ada.server.models.ml.classification.ClassificationResult.{ClassificationResultIdentity, classificationResultFormat}
import org.edena.spark_ml.models.result.ClassificationResult
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

import javax.inject.Inject
import scala.concurrent.Future

class ClassificationResultMongoCrudStore @Inject()(
  @Assisted dataSetId : String,
  dictionaryRepo: DictionaryRootStore
) extends DictionarySubordinateMongoCrudStore[ClassificationResult, BSONObjectID]("classificationResults", dataSetId, dictionaryRepo) {

  private val identity = ClassificationResultIdentity

  override def save(entity: ClassificationResult): Future[BSONObjectID] = {
    val initializedId = identity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(identity.set(entity, initializedId))
  }
}