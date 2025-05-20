package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.ada.server.AdaException
import org.edena.core.field.FieldTypeSpec
import org.edena.store.json.StoreTypes.JsonCrudStore
import org.edena.store.mongo.MongoJsonCrudStoreFactory

import org.edena.core.DefaultTypes.Seq

private[dataaccess] class DummyCachedMongoJsonCrudStoreFactoryImpl extends MongoJsonCrudStoreFactory {

  override def apply(
    collectionName: String,
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    createIndexForProjectionAutomatically: Boolean
  ) : JsonCrudStore =
    throw new AdaException("Caching is not supported/implemented")
}