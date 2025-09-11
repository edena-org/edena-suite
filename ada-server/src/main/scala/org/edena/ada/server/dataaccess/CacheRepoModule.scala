package org.edena.ada.server.dataaccess

import com.google.inject.name.Names
import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.dataaccess.dataset._
import org.edena.ada.server.dataaccess.ignite.{IgniteFactory, IgniteLifecycleBean}
import org.edena.ada.server.dataaccess.ignite.mongo._
import org.edena.ada.server.dataaccess.mongo.dataset._
import org.edena.ada.server.models.{DataSetSetting, DataSetSettingPOJO, DataSpaceMetaInfo, DataSpaceMetaInfoPOJO, HtmlSnippet, HtmlSnippetPOJO, User, UserPOJO}
import org.apache.ignite.Ignite
import org.apache.ignite.lifecycle.LifecycleBean
import org.edena.store.ignite.front.{CustomFromToCacheCrudStoreFactory, CustomFromToCacheCrudStoreFactoryImpl, IdentityCacheCrudStoreFactory, IdentityCacheCrudStoreFactoryImpl, ScalaJavaBinaryCacheCrudStoreFactory, ScalaJavaBinaryCacheCrudStoreFactoryImpl}
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

    bind[IdentityCacheCrudStoreFactory].to(classOf[IdentityCacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[CustomFromToCacheCrudStoreFactory].to(classOf[CustomFromToCacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[ScalaJavaBinaryCacheCrudStoreFactory].to(classOf[ScalaJavaBinaryCacheCrudStoreFactoryImpl]).asEagerSingleton()
    bind[CacheMongoCrudStoreFactory].to(classOf[CacheMongoCrudStoreFactoryImpl]).asEagerSingleton()

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
        usePOJOAccess = true
      )
    ).asEagerSingleton

    bind[UserStore].toProvider(
      new CustomMongoCacheCrudStoreProvider[BSONObjectID, User, String, UserPOJO](
        "users",
        toStoreItem = User.fromPOJO,
        fromStoreItem = User.toPOJO,
        toStoreId = (x: String) => BSONObjectID.parse(x).get,
        fromStoreId = _.stringify,
        usePOJOAccess = true
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

    bind[CategoryStoreFactory].to(classOf[CategoryCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[DataViewStoreFactory].to(classOf[DataViewCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[FieldStoreFactory].to(classOf[FieldCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[FilterStoreFactory].to(classOf[FilterCacheCrudStoreFactoryImpl]).asEagerSingleton()

    bind[DataSetMetaInfoStoreFactory].to(classOf[DataSetMetaInfoCacheCrudStoreFactoryImpl]).asEagerSingleton()
  }
}