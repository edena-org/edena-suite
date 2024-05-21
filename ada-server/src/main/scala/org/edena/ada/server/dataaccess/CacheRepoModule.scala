package org.edena.ada.server.dataaccess

import com.google.inject.name.Names
import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.dataaccess.dataset._
import org.edena.ada.server.dataaccess.ignite.{CacheCrudStoreFactory, CacheCrudStoreFactoryImpl, IgniteFactory, IgniteLifecycleBean}
import org.edena.ada.server.dataaccess.ignite.mongo._
import org.edena.ada.server.dataaccess.mongo.dataset._
import org.edena.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, HtmlSnippet, User}
import org.apache.ignite.Ignite
import org.apache.ignite.lifecycle.LifecycleBean
import org.edena.store.mongo.MongoJsonCrudStoreFactory
import reactivemongo.api.bson.BSONObjectID

class CacheRepoModule extends BaseRepoModule {

  import org.edena.ada.server.models.DataSetFormattersAndIds.{serializableDataSetSettingFormat, serializableDataSpaceMetaInfoFormat, serializableBSONObjectIDFormat, DataSetSettingIdentity, DataSpaceMetaInfoIdentity}
  import org.edena.ada.server.models.User.{serializableUserFormat, UserIdentity}
  import org.edena.ada.server.models.HtmlSnippet.{serializableHtmlSnippetFormat, HtmlSnippetIdentity}

  override def configure = {
    super.configure

    // Ignite Base
    bind[Ignite].toProvider(classOf[IgniteFactory]).asEagerSingleton

    bind[LifecycleBean].to(classOf[IgniteLifecycleBean]).asEagerSingleton()

    // cached mongo JSON CRUD Store Factory
    bind[MongoJsonCrudStoreFactory]
      .annotatedWith(Names.named("CachedJsonCrudStoreFactory"))
      .to(classOf[CachedMongoJsonCrudStoreFactoryImpl])

    ///////////////////////////
    // Cache/Ignite w. Mongo //
    ///////////////////////////

    bind[CacheCrudStoreFactory].to(classOf[CacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[CacheMongoCrudStoreFactory].to(classOf[CacheMongoCrudStoreFactoryImpl]).asEagerSingleton()

    implicit val formatId = serializableBSONObjectIDFormat

    bind[DataSetSettingStore].toProvider(
      new MongoCacheCrudStoreProvider[DataSetSetting, BSONObjectID]("dataset_settings")
    ).asEagerSingleton

    bind[UserStore].toProvider(
      new MongoCacheCrudStoreProvider[User, BSONObjectID]("users")
    ).asEagerSingleton

    bind[DataSpaceMetaInfoStore].toProvider(
      new MongoCacheCrudStoreProvider[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos")
    ).asEagerSingleton

    bind[HtmlSnippetStore].toProvider(
      new MongoCacheCrudStoreProvider[HtmlSnippet, BSONObjectID]("html_snippets")
    ).asEagerSingleton

    bind[CategoryStoreFactory].to(classOf[CategoryCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[DataViewStoreFactory].to(classOf[DataViewCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[FieldStoreFactory].to(classOf[FieldCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[FilterStoreFactory].to(classOf[FilterCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[DataSetMetaInfoStoreFactory].to(classOf[DataSetMetaInfoCacheCrudStoreFactoryImpl]).asEagerSingleton()
  }
}