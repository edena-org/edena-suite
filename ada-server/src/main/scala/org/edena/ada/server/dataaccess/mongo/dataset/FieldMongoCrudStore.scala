package org.edena.ada.server.dataaccess.mongo.dataset

import javax.inject.Inject
import com.google.inject.assistedinject.Assisted
import org.edena.ada.server.models.{DataSetFormattersAndIds, Field}
import DataSetFormattersAndIds._
import org.edena.ada.server.dataaccess.StoreTypes.DictionaryRootStore

class FieldMongoCrudStore @Inject()(
  @Assisted dataSetId : String,
  dictionaryRepo: DictionaryRootStore
) extends DictionarySubordinateMongoCrudStore[Field, String]("fields", dataSetId, dictionaryRepo)

