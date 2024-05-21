package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes.RegressionResultStore

trait RegressionResultStoreFactory {
  def apply(dataSetId: String): RegressionResultStore
}
