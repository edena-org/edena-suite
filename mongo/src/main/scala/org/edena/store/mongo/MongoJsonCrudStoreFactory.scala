package org.edena.store.mongo

import org.edena.core.field.FieldTypeSpec
import org.edena.store.json.StoreTypes.JsonCrudStore
import org.edena.core.DefaultTypes.Seq

trait MongoJsonCrudStoreFactory {

  def apply(
    collectionName: String,
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    createIndexForProjectionAutomatically: Boolean
  ): JsonCrudStore
}
