package org.edena.store.elastic.json

import com.google.inject.assistedinject.Assisted
import org.edena.core.field.FieldTypeSpec
import StoreTypes.ElasticJsonCrudStore
import org.edena.store.elastic.ElasticSetting

import org.edena.core.DefaultTypes.Seq

trait ElasticJsonCrudStoreFactory {

  def apply(
    @Assisted("indexName") indexName : String,
    @Assisted("typeName") typeName : String,
    @Assisted("fieldNamesAndTypes") fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    @Assisted("setting") setting: Option[ElasticSetting],
    @Assisted("excludeIdMapping") excludeIdMapping: Boolean
  ): ElasticJsonCrudStore
}
