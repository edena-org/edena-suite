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

import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

class CacheFactory @Inject()(ignite: Ignite) extends Serializable {

  private val writeThrough = true
  private val readThrough = true

  def apply[ID, STORE_ID, E](
    cacheName: String,
    repoFactory: Factory[CrudStore[E, STORE_ID]],
    getId: E => Option[STORE_ID],
    toStoreId: ID => STORE_ID,
    fromStoreId: STORE_ID => ID,
    fieldsToExcludeFromIndex: Set[String])(
    implicit tagId: ClassTag[ID], typeTagE: TypeTag[E]
  ): IgniteCache[ID, E] =
    apply(
      cacheName,
      Some(new CacheCrudStoreFactory[ID, STORE_ID, E](repoFactory, getId, toStoreId, fromStoreId)),
      fieldsToExcludeFromIndex
    )

  def apply[ID, E](
    cacheName: String,
    cacheStoreFactoryOption: Option[Factory[CacheStore[ID, E]]],
    fieldsToExcludeFromIndex: Set[String])(
    implicit tagId: ClassTag[ID], typeTagE: TypeTag[E]
  ): IgniteCache[ID, E] = {
    val cacheConfig = new CacheConfiguration[ID, E]()

    val fieldNamesAndTypes = IgniteTypeMapper[E]
    val fieldNames = fieldNamesAndTypes.map(_._1)

    val indeces = fieldNames.filterNot(fieldsToExcludeFromIndex.contains).map(new QueryIndex(_)).toSeq

    val queryEntity = new QueryEntity() {
      setKeyType(tagId.runtimeClass.getName)
      setValueType(typeOf[E].typeSymbol.fullName)
      setFields(new java.util.LinkedHashMap[String, String](fieldNamesAndTypes.toMap.asJava))
      setIndexes(indeces.asJava)
    }

    cacheConfig.setSqlFunctionClasses(classOf[CustomSqlFunctions])
    cacheConfig.setName(cacheName)
    cacheConfig.setQueryEntities(Seq(queryEntity).asJava)
    cacheConfig.setCacheMode(CacheMode.LOCAL) // REPLICATED
    cacheConfig.setAtomicityMode(CacheAtomicityMode.ATOMIC)
//    cacheConfig.setAtomicWriteOrderMode(CacheAtomicWriteOrderMode.PRIMARY) was available in the version 1.6

    cacheStoreFactoryOption.foreach{ cacheStoreFactory =>
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

object IgniteTypeMapper {

  private val optionType = typeOf[Option[_]]

  private implicit class InfixOp(val typ: Type) {

    private val optionInnerType =
      if (typ <:< optionType)
        Some(typ.typeArgs.head)
      else
        None

    // TODO: handle an Option type for value write/read
//    def matches(types: Type*) =
//      types.exists(typ =:= _) ||
//        (optionInnerType.isDefined && types.exists(optionInnerType.get =:= _))
//
//    def subMatches(types: Type*) =
//      types.exists(typ <:< _) ||
//        (optionInnerType.isDefined && types.exists(optionInnerType.get <:< _))

    def matches(types: Type*) =
      types.exists(typ =:= _)

    def subMatches(types: Type*) =
      types.exists(typ <:< _)
  }

  // source: https://ignite.apache.org/docs/latest/sql-reference/data-types
  object IgniteTypes {
    val boolean = classOf[jl.Boolean]
    val long = classOf[jl.Long]
    val bigDecimal = classOf[java.math.BigDecimal]
    val double = classOf[jl.Double]
    val integer = classOf[jl.Integer]
    val float = classOf[jl.Float]
    val short = classOf[jl.Short]
    val byte = classOf[jl.Byte]
    val string = classOf[jl.String]
    val timeStamp = classOf[java.sql.Timestamp]
    val binary = classOf[Array[Byte]]
    val uuid = classOf[ju.UUID]
  }

  def apply[E: TypeTag]: Traversable[(String, String)] = {
    val fieldNamesAndTypes = getCaseClassMemberNamesAndTypes(typeOf[E])

    fieldNamesAndTypes.map { case (fieldName, fieldType) =>
      val igniteFieldType: Class[_] = fieldType match {
        // boolean
        case t if t matches(typeOf[Boolean], typeOf[jl.Boolean]) => IgniteTypes.boolean

        // long
        case t if t matches(typeOf[Long], typeOf[jl.Long]) => IgniteTypes.long

        // big decimal
        case t if t matches(typeOf[BigDecimal], typeOf[java.math.BigDecimal]) => IgniteTypes.bigDecimal

        // double
        case t if t matches(typeOf[Double], typeOf[jl.Double]) => IgniteTypes.double

        // int
        case t if t matches(typeOf[Int], typeOf[jl.Integer]) => IgniteTypes.integer

        // float
        case t if t matches(typeOf[Float], typeOf[jl.Float]) => IgniteTypes.float

        // smallInt
        case t if t matches(typeOf[Short], typeOf[jl.Short]) => IgniteTypes.short

        // byte
        case t if t matches(typeOf[Byte], typeOf[jl.Byte]) => IgniteTypes.byte

        // timestamp/date
        case t if t matches(typeOf[ju.Date], typeOf[java.sql.Timestamp]) => IgniteTypes.timeStamp

        // binary
        case t if t matches(typeOf[Array[Byte]], typeOf[Array[jl.Byte]]) => IgniteTypes.binary

        // UUID
        case t if t matches typeOf[ju.UUID] => IgniteTypes.uuid

        // string
        case t if t matches typeOf[String] => IgniteTypes.string

        // everything else report as it is
        case _ => ReflectionUtil.typeToClass(fieldType)
      }

      (fieldName, igniteFieldType.getName)
    }
  }
}