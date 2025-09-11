package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.ada.server.dataaccess._
import org.edena.ada.server.field.FieldTypeFactory
import org.apache.ignite.Ignite
import org.edena.core.field.FieldTypeSpec
import org.edena.store.ignite.{BinaryCacheFactory, BinaryJsonHelper}
import org.edena.store.ignite.front.JsonBinaryCacheWrappingCrudStore
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.StoreTypes.JsonCrudStore
import org.edena.store.mongo.{MongoJsonCrudStoreFactory, PlayMongoJsonRepoFactory}
import play.api.Configuration
import reactivemongo.api.bson.BSONObjectID

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global

private[dataaccess] class PlayCachedMongoJsonCrudStoreFactoryImpl @Inject()(
  ignite: Ignite,
  cacheFactory: BinaryCacheFactory,
  configuration: Configuration
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

    val cache = cacheFactory.withJson(
      cacheName,
      fieldNamesAndClasses.toMap.mapValues(_.getName).toMap,
      fieldsToExcludeFromIndex = Set(),
      new PlayMongoJsonRepoFactory(collectionName, createIndexForProjectionAutomatically, configuration, new SerializableApplicationLifecycle()),
      identity.of(_)
    ) // new DefaultApplicationLifecycle().addStopHook
    cache.loadCache(null)
    new JsonBinaryCacheWrappingCrudStore(cache, cacheName, ignite, identity)
  }
}