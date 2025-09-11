package org.edena.ada.server.dataaccess

import com.google.inject.name.Names
import com.sksamuel.elastic4s.ElasticClient
import net.codingwell.scalaguice.ScalaModule
import org.edena.ada.server.dataaccess.StoreTypes.{DataSetSettingStore, DataSpaceMetaInfoStore, HtmlSnippetStore, UserStore}
import org.edena.ada.server.dataaccess.dataset._
import org.edena.ada.server.dataaccess.elastic.PlayElasticClientProvider
import org.edena.ada.server.dataaccess.ignite.mongo._
import org.edena.ada.server.dataaccess.ignite.{IgniteFactory, PlayIgniteLifecycleBean}
import org.edena.ada.server.models.DataSetFormattersAndIds.serializableBSONObjectIDFormat
import org.edena.ada.server.models.{DataSetSetting, DataSetSettingPOJO, DataSpaceMetaInfo, DataSpaceMetaInfoPOJO, HtmlSnippet, HtmlSnippetPOJO, User, UserPOJO}
import org.apache.ignite.Ignite
import org.apache.ignite.lifecycle.LifecycleBean
import org.edena.store.ignite.front.{CustomFromToCacheCrudStoreFactory, CustomFromToCacheCrudStoreFactoryImpl, IdentityCacheCrudStoreFactory, IdentityCacheCrudStoreFactoryImpl, ScalaJavaBinaryCacheCrudStoreFactory, ScalaJavaBinaryCacheCrudStoreFactoryImpl}
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

    bind[IdentityCacheCrudStoreFactory].to(classOf[IdentityCacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[CustomFromToCacheCrudStoreFactory].to(classOf[CustomFromToCacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[ScalaJavaBinaryCacheCrudStoreFactory].to(classOf[ScalaJavaBinaryCacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[CacheMongoCrudStoreFactory].to(classOf[PlayCacheMongoCrudStoreFactoryImpl]).asEagerSingleton()

    implicit val formatId = serializableBSONObjectIDFormat

//    bind[DataSetSettingStore].toProvider(
//      new IdentityMongoCacheCrudStoreProvider[DataSetSetting, BSONObjectID]("dataset_settings")
//    ).asEagerSingleton

    bind[DataSetSettingStore].toProvider(
      new CustomMongoCacheCrudStoreProvider[BSONObjectID, DataSetSetting, String, DataSetSettingPOJO](
        "dataset_settings",
        toStoreItem = DataSetSetting.fromPOJO,
        fromStoreItem = DataSetSetting.toPOJO,
        toStoreId = (x: String) => BSONObjectID.parse(x).get,
        fromStoreId = _.stringify,
        usePOJOAccess = true,
        fieldsToExcludeFromIndex = Set("originalItem")
      )
    ).asEagerSingleton

    bind[UserStore].toProvider(
      new CustomMongoCacheCrudStoreProvider[BSONObjectID, User, String, UserPOJO](
        "users",
        toStoreItem = User.fromPOJO,
        fromStoreItem = User.toPOJO,
        toStoreId = (x: String) => BSONObjectID.parse(x).get,
        fromStoreId = _.stringify,
        usePOJOAccess = true,
        fieldsToExcludeFromIndex = Set("roles", "permissions")
      )
    ).asEagerSingleton

    bind[DataSpaceMetaInfoStore].toProvider(
      new CustomMongoCacheCrudStoreProvider[BSONObjectID, DataSpaceMetaInfo, String, DataSpaceMetaInfoPOJO](
        "dataspace_meta_infos",
        toStoreItem = DataSpaceMetaInfo.fromPOJO,
        fromStoreItem = DataSpaceMetaInfo.toPOJO,
        toStoreId = (x: String) => BSONObjectID.parse(x).get,
        fromStoreId = _.stringify,
        usePOJOAccess = true,
        fieldsToExcludeFromIndex = Set("originalItem")
      )
    ).asEagerSingleton

    bind[HtmlSnippetStore].toProvider(
      new CustomMongoCacheCrudStoreProvider[BSONObjectID, HtmlSnippet, String, HtmlSnippetPOJO](
        "html_snippets",
        toStoreItem = HtmlSnippet.fromPOJO,
        fromStoreItem = HtmlSnippet.toPOJO,
        toStoreId = (x: String) => BSONObjectID.parse(x).get,
        fromStoreId = _.stringify,
        usePOJOAccess = true
      )
    ).asEagerSingleton

    bind[CategoryStoreFactory].to(classOf[PlayCategoryCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[DataViewStoreFactory].to(classOf[PlayDataViewCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[FieldStoreFactory].to(classOf[PlayFieldCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[FilterStoreFactory].to(classOf[PlayFilterCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[DataSetMetaInfoStoreFactory].to(classOf[PlayDataSetMetaInfoCacheCrudStoreFactoryImpl]).asEagerSingleton()
  }
}