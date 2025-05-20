package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes.DataSetMetaInfoStore
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.DefaultTypes.Seq

trait DataSetMetaInfoStoreFactory {
  def apply(dataSpaceId: BSONObjectID): DataSetMetaInfoStore
}
