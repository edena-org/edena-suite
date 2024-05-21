package org.edena.store.ignite

import javax.cache.configuration.Factory
import javax.inject.Inject
import org.edena.store.ignite._
import org.edena.core.util.DynamicConstructorFinder
import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.Identity
import org.edena.core.util.ReflectionUtil.shortName
import org.edena.core.store._
//import play.api.Configuration
//import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Format

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.{TypeTag, typeOf}
import scala.reflect.ClassTag

class CacheCrudStore[ID, E: TypeTag](
    cache: IgniteCache[ID, E],
    entityName: String,
    val ignite: Ignite,
    identity: Identity[E, ID]
  ) extends AbstractCacheCrudStore[ID, E, ID, E](cache, entityName, identity) {

  private val constructorFinder = DynamicConstructorFinder.apply[E]

  // hooks
  override def toCacheId(id: ID) =
    id

  override def toItem(cacheItem: E) =
//    cacheItem.asInstanceOf[BinaryObject].deserialize[E]
    cacheItem

  override def toCacheItem(item: E) =
    item

  override def findResultToItem(result: Traversable[(String, Any)]): E = {
    val fieldNameValueMap = result.map{ case (fieldName, value) => (unescapeFieldName(fieldName), value) }.toMap
    val fieldNames = fieldNameValueMap.map(_._1).toSeq

    val constructor = constructorOrException(fieldNames)

    constructor(fieldNameValueMap).get
  }

  override def findResultsToItems(rawFieldNames: Seq[String], results: Traversable[Seq[Any]]): Traversable[E] = {
    val fieldNames = rawFieldNames.map(unescapeFieldName)

    val constructor = constructorOrException(fieldNames)

    results.map { result =>
      // TODO: which one is faster? First or second constructor call?
      constructor(fieldNames.zip(result).toMap).getOrElse(
        throw new EdenaDataStoreException(s"Constructor of the class '${constructorFinder.classSymbol.fullName}' with the fields '${fieldNames.mkString(", ")}' returned a null item.")
      )

//      constructor(result).get
    }
  }

  private def constructorOrException(fieldNames: Seq[String]) =
    constructorFinder(fieldNames).getOrElse{
      throw new EdenaDataStoreException(s"No constructor of the class '${constructorFinder.classSymbol.fullName}' matches the query result fields '${fieldNames.mkString(", ")}'. Adjust your query or introduce an appropriate constructor.")
    }
}