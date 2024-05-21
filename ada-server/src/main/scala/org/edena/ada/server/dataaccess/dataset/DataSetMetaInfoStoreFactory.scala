package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes.DataSetMetaInfoStore
import reactivemongo.api.bson.BSONObjectID

trait DataSetMetaInfoStoreFactory {
  def apply(dataSpaceId: BSONObjectID): DataSetMetaInfoStore
}
