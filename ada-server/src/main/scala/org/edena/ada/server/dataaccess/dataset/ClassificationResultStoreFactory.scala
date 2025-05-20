package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes.ClassificationResultStore
import org.edena.core.DefaultTypes.Seq

trait ClassificationResultStoreFactory {
  def apply(dataSetId: String): ClassificationResultStore
}
