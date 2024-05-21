package org.edena.ada.server.dataaccess

import com.google.inject.name.Names
import com.sksamuel.elastic4s.ElasticClient
import net.codingwell.scalaguice.ScalaModule
import org.edena.ada.server.dataaccess.StoreTypes.{DataSetSettingStore, DataSpaceMetaInfoStore, HtmlSnippetStore, UserStore}
import org.edena.ada.server.dataaccess.dataset._
import org.edena.ada.server.dataaccess.elastic.PlayElasticClientProvider
import org.edena.ada.server.dataaccess.ignite.mongo._
import org.edena.ada.server.dataaccess.ignite.{CacheCrudStoreFactory, CacheCrudStoreFactoryImpl, IgniteFactory, PlayIgniteLifecycleBean}
import org.edena.ada.server.models.DataSetFormattersAndIds.serializableBSONObjectIDFormat
import org.edena.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, HtmlSnippet, User}
import org.apache.ignite.Ignite
import org.apache.ignite.lifecycle.LifecycleBean
import org.edena.store.mongo.MongoJsonCrudStoreFactory
import reactivemongo.api.bson.BSONObjectID

class PlayCacheRepoModule extends BaseRepoModule {

  import org.edena.ada.server.models.DataSetFormattersAndIds.{serializableDataSetSettingFormat, serializableDataSpaceMetaInfoFormat, serializableBSONObjectIDFormat, DataSetSettingIdentity, DataSpaceMetaInfoIdentity}
  import org.edena.ada.server.models.User.{serializableUserFormat, UserIdentity}
  import org.edena.ada.server.models.HtmlSnippet.{serializableHtmlSnippetFormat, HtmlSnippetIdentity}

  override def configure = {
    super.configure

    // Ignite base
    bind[Ignite].toProvider(classOf[IgniteFactory]).asEagerSingleton

    bind[LifecycleBean].to(classOf[PlayIgniteLifecycleBean]).asEagerSingleton()

    // Play-based elastic client provider
    bind[ElasticClient].toProvider(new PlayElasticClientProvider).asEagerSingleton

    // Play-based cached mongo JSON CRUD Store Factory
    bind[MongoJsonCrudStoreFactory]
      .annotatedWith(Names.named("CachedJsonCrudStoreFactory"))
      .to(classOf[PlayCachedMongoJsonCrudStoreFactoryImpl])

    ///////////////////////////
    // Cache/Ignite w. Mongo //
    ///////////////////////////

    bind[CacheCrudStoreFactory].to(classOf[CacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[CacheMongoCrudStoreFactory].to(classOf[PlayCacheMongoCrudStoreFactoryImpl]).asEagerSingleton()

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

    bind[CategoryStoreFactory].to(classOf[PlayCategoryCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[DataViewStoreFactory].to(classOf[PlayDataViewCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[FieldStoreFactory].to(classOf[PlayFieldCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[FilterStoreFactory].to(classOf[PlayFilterCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[DataSetMetaInfoStoreFactory].to(classOf[PlayDataSetMetaInfoCacheCrudStoreFactoryImpl]).asEagerSingleton()
  }
}