package org.edena.ada.server.dataaccess.mongo.dataset

import com.google.inject.assistedinject.Assisted
import org.edena.ada.server.dataaccess.StoreTypes.DictionaryRootStore
import org.edena.ada.server.models.ml.regression.RegressionResult.{RegressionResultIdentity, regressionResultFormat}
import org.edena.spark_ml.models.result.RegressionResult
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

import javax.inject.Inject
import scala.concurrent.Future

class RegressionResultMongoCrudStore @Inject()(
  @Assisted dataSetId : String,
  dictionaryRepo: DictionaryRootStore
) extends DictionarySubordinateMongoCrudStore[RegressionResult, BSONObjectID]("regressionResults", dataSetId, dictionaryRepo) {

  private val identity = RegressionResultIdentity

  override def save(entity: RegressionResult): Future[BSONObjectID] = {
    val initializedId = identity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(identity.set(entity, initializedId))
  }
}