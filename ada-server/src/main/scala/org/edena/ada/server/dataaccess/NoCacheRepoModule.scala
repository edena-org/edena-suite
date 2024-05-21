package org.edena.ada.server.dataaccess

import com.google.inject.TypeLiteral
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.name.Names
import net.codingwell.scalaguice.ScalaModule
import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.dataaccess.dataset._
import org.edena.ada.server.dataaccess.ignite.mongo._
import org.edena.ada.server.dataaccess.mongo.dataset._
import org.edena.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, HtmlSnippet, User}
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.models.DataSetFormattersAndIds
import DataSetFormattersAndIds.{DataSetSettingIdentity, DataSpaceMetaInfoIdentity}
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{MongoCrudStore, MongoJsonCrudStoreFactory}

import scala.concurrent.ExecutionContext

class NoCacheRepoModule extends BaseRepoModule {

  private implicit val dataSetSettingFormat = DataSetFormattersAndIds.dataSetSettingFormat
  private implicit val dataSpaceMetaInfoFormat = DataSetFormattersAndIds.dataSpaceMetaInfoFormat
  private implicit val usersFormat = User.userFormat
  private implicit val htmlSnippetFormat = HtmlSnippet.htmlSnippetFormat

  override def configure = {
    super.configure

    // cached mongo JSON CRUD Store Factory
    bind[MongoJsonCrudStoreFactory]
      .annotatedWith(Names.named("CachedJsonCrudStoreFactory"))
      .to(classOf[DummyCachedMongoJsonCrudStoreFactoryImpl])

    //////////////////////////
    // No Cache Mongo Repos //
    //////////////////////////

    bind[DataSetSettingStore].toInstance(
      new MongoCrudStore[DataSetSetting, BSONObjectID]("dataset_settings")
    )

    bind[UserStore].toInstance(
      new MongoCrudStore[User, BSONObjectID]("users")
    )

    bind[DataSpaceMetaInfoStore].toInstance(
      new MongoCrudStore[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos")
    )

    bind[HtmlSnippetStore].toInstance(
      new MongoCrudStore[HtmlSnippet, BSONObjectID]("html_snippets")
    )

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[CategoryStore]{}, classOf[CategoryMongoCrudStore])
      .build(classOf[CategoryStoreFactory]))

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[DataViewStore]{}, classOf[DataViewMongoCrudStore])
      .build(classOf[DataViewStoreFactory]))

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[FieldStore]{}, classOf[FieldMongoCrudStore])
      .build(classOf[FieldStoreFactory]))

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[FilterStore]{}, classOf[FilterMongoCrudStore])
      .build(classOf[FilterStoreFactory]))

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[DataSetMetaInfoStore]{}, classOf[DataSetMetaInfoMongoCrudStore])
      .build(classOf[DataSetMetaInfoStoreFactory]))
  }
}