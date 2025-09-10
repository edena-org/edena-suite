package org.edena.store.ignite

import org.apache.ignite.cache.store.CacheStore

import java.{lang => jl, util => ju}
import scala.reflect.runtime.universe._
import java.io.Serializable
import javax.cache.configuration.Factory
import javax.inject.Inject
import org.edena.core.store.CrudStore
import org.edena.core.util.ReflectionUtil.getCaseClassMemberNamesAndTypes
import org.apache.ignite.cache._
import org.apache.ignite.configuration.{BinaryConfiguration, CacheConfiguration}
import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.util.ReflectionUtil
import org.edena.store.ignite.persistence.{
  FromToCacheToCrudPersistenceStoreFactory,
  FromToCacheToCrudPersistenceStoreFactoryCentral
}

import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/**
 * Central factory to create Ignite caches with appropriate configuration
 *
 * @param ignite
 */
class CacheFactory @Inject() (ignite: Ignite) extends Serializable {

  private val writeThrough = true
  private val readThrough = true

  def withCustom[ID, E, STORE_ID, STORE_E](
    cacheName: String,
    persistenceStoreFactory: Factory[CrudStore[STORE_E, STORE_ID]],
    getId: STORE_E => Option[STORE_ID],
    toStoreId: ID => STORE_ID,
    fromStoreId: STORE_ID => ID,
    toStoreItem: E => STORE_E,
    fromStoreItem: STORE_E => E,
    fieldsToExcludeFromIndex: Set[String]
  )(
    implicit tagId: ClassTag[ID],
    typeTagE: TypeTag[E], classTag: ClassTag[E]
  ): IgniteCache[ID, E] =
    apply(
      cacheName,
      Some(
        new FromToCacheToCrudPersistenceStoreFactory[ID, E, STORE_ID, STORE_E](
          persistenceStoreFactory,
          getId,
          toStoreId,
          fromStoreId,
          toStoreItem,
          fromStoreItem
        )
      ),
      fieldsToExcludeFromIndex
    )

  def withSameItemType[ID, STORE_ID, E](
    cacheName: String,
    persistenceStoreFactory: Factory[CrudStore[E, STORE_ID]],
    getId: E => Option[STORE_ID],
    toStoreId: ID => STORE_ID,
    fromStoreId: STORE_ID => ID,
    fieldsToExcludeFromIndex: Set[String]
  )(
    implicit tagId: ClassTag[ID],
    typeTagE: TypeTag[E], classTag: ClassTag[E]
  ): IgniteCache[ID, E] =
    apply(
      cacheName,
      Some(
        new FromToCacheToCrudPersistenceStoreFactory[ID, E, STORE_ID, E](
          persistenceStoreFactory,
          getId,
          toStoreId,
          fromStoreId,
          toStoreItem = identity[E] _,
          fromStoreItem = identity[E] _
        )
      ),
      fieldsToExcludeFromIndex
    )

  def apply[ID, E](
    cacheName: String,
    cacheStoreFactoryOption: Option[Factory[CacheStore[ID, E]]],
    fieldsToExcludeFromIndex: Set[String]
  )(
    implicit tagId: ClassTag[ID],
    typeTagE: TypeTag[E], classTag: ClassTag[E]
  ): IgniteCache[ID, E] = {
    val cacheConfig = new CacheConfiguration[ID, E]()

    val fieldNamesAndTypes = IgniteTypeMapper[E]
    val fieldNames = fieldNamesAndTypes.map(_._1)

    val indeces = fieldNames
      .filterNot(
        fieldsToExcludeFromIndex.contains
      )
      .map(new QueryIndex(_))
      .toSeq

    val valueType = typeOf[E].typeSymbol.fullName
    val tableName = typeOf[E].typeSymbol.fullName.split("\\.").lastOption.getOrElse("Unknown").stripSuffix("POJO")

    val queryEntity = new QueryEntity() {
      setKeyType(tagId.runtimeClass.getName)
      setValueType(valueType)
      setTableName(tableName)
      setFields(new java.util.LinkedHashMap[String, String](fieldNamesAndTypes.toMap.asJava))
      setIndexes(indeces.asJava)
    }

    println(s"Creating cache '$cacheName' with fields and types:")
    println(fieldNames.mkString("\n"))
    println("Value type is: " + queryEntity.getValueType)
    println("Table name is: " + queryEntity.getTableName)

    cacheConfig.setSqlFunctionClasses(classOf[CustomSqlFunctions])
    cacheConfig.setName(cacheName)
    cacheConfig.setQueryEntities(Seq(queryEntity).asJava)
    cacheConfig.setCacheMode(CacheMode.REPLICATED) // CacheMode.LOCAL) // REPLICATED
    cacheConfig.setAtomicityMode(CacheAtomicityMode.ATOMIC)
//    cacheConfig.setAtomicWriteOrderMode(CacheAtomicWriteOrderMode.PRIMARY) was available in the version 1.6

    cacheStoreFactoryOption.foreach { cacheStoreFactory =>
      cacheConfig.setCacheStoreFactory(cacheStoreFactory)
      cacheConfig.setWriteThrough(writeThrough)
//      cacheConfig.setWriteBehindEnabled(!writeThrough)
//      cacheConfig.setWriteBehindBatchSize()
      cacheConfig.setReadThrough(readThrough)
    }

//    val bCfg = new BinaryConfiguration()
//    bCfg.setIdMapper(new BinaryBasicIdMapper)
//    bCfg.setTypeConfigurations(util.Arrays.asList(new BinaryTypeConfiguration("org.my.Class")))

    ignite.getOrCreateCache(cacheConfig) // .withKeepBinary()
  }
}