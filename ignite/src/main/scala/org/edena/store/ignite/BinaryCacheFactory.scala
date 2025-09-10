package org.edena.store.ignite

import org.apache.ignite.binary.BinaryObject
import org.apache.ignite.cache.store.CacheStore
import org.apache.ignite.cache.{CacheAtomicityMode, CacheMode, QueryEntity, QueryIndex}
import org.apache.ignite.configuration.CacheConfiguration
import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.DefaultTypes.Seq
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.core.store._
import org.edena.store.ignite.persistence.FromToCacheToCrudPersistenceStoreFactoryCentral
import play.api.libs.json.JsObject

import javax.cache.configuration.Factory
import javax.inject.Inject
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}

class BinaryCacheFactory @Inject() (ignite: Ignite) extends BinaryJsonHelper with Serializable {

//  private val ftf = FieldTypeHelper.fieldTypeFactory()

  def withJson[ID](
    cacheName: String,
    fieldNameTypeNameMap: Map[String, String],
    fieldsToExcludeFromIndex: Set[String] = Set(),
    repoFactory: Factory[CrudStore[JsObject, ID]],
    getId: JsObject => Option[ID]
  )(
    implicit tagId: ClassTag[ID]
  ): IgniteCache[ID, BinaryObject] = {
//    val fieldNameClassMap = createFieldNameClassMapFromDictionary(idFieldName, fieldNamesAndTypes)
    val cacheStoreFactory: Factory[CacheStore[ID, BinaryObject]] =
      FromToCacheToCrudPersistenceStoreFactoryCentral.withJsonBinary[ID](
        cacheName,
        ignite,
        repoFactory,
        getId,
        // fieldNameTypeNameMap
      )

    apply(
      cacheName,
      fieldNameTypeNameMap,
      fieldsToExcludeFromIndex,
      Some(cacheStoreFactory)
    )
  }

  def withScalaJava[ID, E](
    cacheName: String,
    fieldNameTypeNameMap: Map[String, String],
    fieldsToExcludeFromIndex: Set[String] = Set(),
    persistenceStoreFactory: Factory[CrudStore[E, ID]],
    getId: E => Option[ID]
  )(
    implicit tagId: ClassTag[ID], tagItem: ru.TypeTag[E]
  ): IgniteCache[ID, BinaryObject] = {
    val cacheStoreFactory: Factory[CacheStore[ID, BinaryObject]] =
      FromToCacheToCrudPersistenceStoreFactoryCentral.withScalaJavaBinary[ID, E](
        ignite,
        persistenceStoreFactory,
        getId
      )

    apply(
      cacheName,
      fieldNameTypeNameMap,
      fieldsToExcludeFromIndex,
      Some(cacheStoreFactory)
    )
  }

  def apply[ID](
    cacheName: String,
    fieldNameTypeNameMap: Map[String, String],
    fieldsToExcludeFromIndex: Set[String] = Set(),
    cacheStoreFactoryOption: Option[Factory[CacheStore[ID, BinaryObject]]]
  )(
    implicit tagId: ClassTag[ID]
  ): IgniteCache[ID, BinaryObject] = {
    val fieldNames = fieldNameTypeNameMap.keys

    val indeces = fieldNames
      .filterNot(
        fieldsToExcludeFromIndex.contains
      )
      .map(new QueryIndex(_))
      .toSeq

    val queryEntity = new QueryEntity() {
      setKeyType(tagId.runtimeClass.getName)
      setValueType(cacheName)
//      setValueType(typeOf[E].typeSymbol.fullName)
      setFields(new java.util.LinkedHashMap[String, String](fieldNameTypeNameMap.asJava))
      setIndexes(indeces.asJava)
    }

    val cacheConfig = new CacheConfiguration[ID, BinaryObject]()

    cacheConfig.setSqlFunctionClasses(classOf[CustomSqlFunctions])
    cacheConfig.setName(cacheName)
    cacheConfig.setQueryEntities(Seq(queryEntity).asJava)
    cacheConfig.setCacheMode(CacheMode.REPLICATED)
    cacheConfig.setAtomicityMode(CacheAtomicityMode.ATOMIC)

    cacheStoreFactoryOption.foreach { cacheStoreFactory =>
      cacheConfig.setCacheStoreFactory(cacheStoreFactory)
      cacheConfig.setWriteThrough(true)
      cacheConfig.setReadThrough(true)
    }

    ignite.getOrCreateCache(cacheConfig).withKeepBinary()
  }

//  private def createFieldNameTypeMapFromDictionary[ID](
//    idFieldName: String,
//    fieldNamesAndTypes: Seq[(String, FieldTypeId.Value)]
//  )(
//    implicit tagId: ClassTag[ID]
//  ): Map[String, String] =
//    (
//      fieldNamesAndTypes.map { case (fieldName, fieldType) =>
//        (escapeIgniteFieldName(fieldName), ftf(FieldTypeSpec(fieldType)).valueClass.getName)
//      } ++
//        Seq((idFieldName, tagId.runtimeClass.getName))
//    ).toMap
//
//  private def createFieldNameClassMapFromDictionary[ID](
//    idFieldName: String,
//    fieldNamesAndTypes: Seq[(String, FieldTypeId.Value)]
//  )(
//    implicit tagId: ClassTag[ID]
//  ): Map[String, Class[_]] =
//    (
//      fieldNamesAndTypes.map { case (fieldName, fieldType) =>
//        (
//          escapeIgniteFieldName(fieldName),
//          ftf(FieldTypeSpec(fieldType)).valueClass.asInstanceOf[Class[_ >: Any]]
//        )
//      } ++
//        Seq((idFieldName, tagId.runtimeClass.asInstanceOf[Class[_ >: Any]]))
//    ).toMap
}
