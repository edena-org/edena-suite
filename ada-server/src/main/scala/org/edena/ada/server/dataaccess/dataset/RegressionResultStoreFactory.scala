package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes.RegressionResultStore
import org.edena.core.DefaultTypes.Seq

trait RegressionResultStoreFactory {
  def apply(dataSetId: String): RegressionResultStore
}
