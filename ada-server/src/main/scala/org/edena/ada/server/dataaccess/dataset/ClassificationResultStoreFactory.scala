package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes.ClassificationResultStore

trait ClassificationResultStoreFactory {
  def apply(dataSetId: String): ClassificationResultStore
}
