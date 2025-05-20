package org.edena.ada.server.dataaccess.ignite.mongo

import com.typesafe.config.Config
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.dataaccess.ignite.{BinaryCacheFactory, JsonBinaryCacheCrudStore}
import org.edena.ada.server.field.FieldTypeFactory
import org.apache.ignite.Ignite
import org.edena.core.field.FieldTypeSpec
import org.edena.store.ignite.BinaryJsonHelper
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.StoreTypes.JsonCrudStore
import org.edena.store.mongo.{CommonMongoJsonRepoFactory, MongoJsonCrudStoreFactory}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import javax.inject.Inject
import org.edena.core.DefaultTypes.Seq

private[dataaccess] class CachedMongoJsonCrudStoreFactoryImpl @Inject()(
  ignite: Ignite,
  cacheFactory: BinaryCacheFactory,
  config: Config
) extends MongoJsonCrudStoreFactory with BinaryJsonHelper {

  private val ftf = FieldTypeFactory(Set[String](""), Seq[String](), "", ",", true)

  override def apply(
    collectionName: String,
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    createIndexForProjectionAutomatically: Boolean
  ) : JsonCrudStore = {
    val cacheName = collectionName.replaceAll("[\\.-]", "_")
    val identity = JsObjectIdentity

    val fieldNamesAndClasses: Seq[(String, Class[_ >: Any])] =
      (fieldNamesAndTypes.map{ case (fieldName, fieldTypeSpec) =>
        (escapeIgniteFieldName(fieldName), ftf(fieldTypeSpec).valueClass.asInstanceOf[Class[_ >: Any]])
      } ++ Seq((identity.name, classOf[Option[BSONObjectID]].asInstanceOf[Class[_ >: Any]])))

    val cache = cacheFactory(
      cacheName,
      fieldNamesAndClasses,
      new CommonMongoJsonRepoFactory(collectionName, createIndexForProjectionAutomatically, config),
      identity.of(_)
    )
    // new DefaultApplicationLifecycle().addStopHook

    cache.loadCache(null)
    new JsonBinaryCacheCrudStore(cache, cacheName, ignite, identity)
  }
}