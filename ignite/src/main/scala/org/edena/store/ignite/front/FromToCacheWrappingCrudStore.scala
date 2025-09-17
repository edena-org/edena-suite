package org.edena.store.ignite.front

import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.Identity
import org.edena.core.store._
import org.edena.core.util.{DynamicConstructorFinder, LoggingSupport}
import org.edena.core.DefaultTypes.Seq

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

class FromToCacheWrappingCrudStore[ID: ClassTag, E, CACHE_ID, CACHE_E: TypeTag](
  cache: IgniteCache[CACHE_ID, CACHE_E],
  entityName: String,
  val ignite: Ignite,
  identity: Identity[E, ID],
  usePOJOAccess: Boolean = false
)(
  fromCache: CACHE_E => E,
  toCache: E => CACHE_E,
  asCacheId: ID => CACHE_ID
) extends AbstractCacheWrappingCrudStore[ID, E, CACHE_ID, CACHE_E](cache, entityName, identity) {

//  private val frontItemConstructorFinder = DynamicConstructorFinder.apply[E]

  private val cacheItemConstructorFinder = DynamicConstructorFinder.apply[CACHE_E]

  // hooks
  override def toCacheId(id: ID): CACHE_ID = asCacheId(id)

  override def toItem(cacheItem: CACHE_E): E = fromCache(cacheItem)

  override def toCacheItem(item: E): CACHE_E = toCache(item)

  override def findResultToItem(result: Traversable[(String, Any)]): E = {
    if (usePOJOAccess) {
      findResultToItemPOJO(result)
    } else {
      val fieldNameValueMap = result.map { case (fieldName, value) =>
        (unescapeFieldName(fieldName), value)
      }.toMap
      val fieldNames = fieldNameValueMap.map(_._1).toSeq

      val constructor = cacheItemConstructorOrError(fieldNames)

      val cacheItem = constructor(fieldNameValueMap).get

      toItem(cacheItem)
    }
  }

  override def findResultsToItems(
    rawFieldNames: Seq[String],
    results: Traversable[Seq[Any]]
  ): Traversable[E] = {
    if (usePOJOAccess) {
      findResultsToItemsPOJO(rawFieldNames, results)
    } else {
      val fieldNames = rawFieldNames.map(unescapeFieldName)

      val constructor = cacheItemConstructorOrError(fieldNames)

      results.map { result =>
        // TODO: which one is faster? First or second constructor call?
        val cacheItem = constructor(fieldNames.zip(result).toMap).getOrElse(
          throw new EdenaDataStoreException(
            s"Constructor of the class '${cacheItemConstructorFinder.classSymbol.fullName}' with the fields '${fieldNames
                .mkString(", ")}' returned a null item."
          )
        )
        //      constructor(result).get

        toItem(cacheItem)
      }
    }
  }

//  private def frontItemConstructorOrError(fieldNames: Seq[String]) =
//    frontItemConstructorFinder(fieldNames.toSeq).getOrElse{
//      throw new EdenaDataStoreException(s"No constructor of the class '${frontItemConstructorFinder.classSymbol.fullName}' matches the query result fields '${fieldNames.mkString(", ")}'. Adjust your query or introduce an appropriate constructor.")
//    }

  private def cacheItemConstructorOrError(fieldNames: Seq[String]) =
    cacheItemConstructorFinder(fieldNames.toSeq).getOrElse {
      throw new EdenaDataStoreException(
        s"No constructor of the class '${cacheItemConstructorFinder.classSymbol.fullName}' matches the query result fields '${fieldNames
            .mkString(", ")}'. Adjust your query or introduce an appropriate constructor."
      )
    }

  // POJO construction methods
  private def findResultToItemPOJO(result: Traversable[(String, Any)]): E = {
    try {
      val fieldNameValueMap = result.map { case (fieldName, value) =>
        val cleanFieldName = unescapeFieldName(fieldName)
        (cleanFieldName, value)
      }.toMap

      val cacheItem = createPOJOFromMap(fieldNameValueMap)
      val domainItem = toItem(cacheItem)
      domainItem
    } catch {
      case e: Exception =>
        val message = s"Failed to create POJO from SQL result for $entityName"
        logger.error(message, e)
        throw new EdenaDataStoreException(message, e)
    }
  }

  private def findResultsToItemsPOJO(
    rawFieldNames: Seq[String],
    results: Traversable[Seq[Any]]
  ): Traversable[E] = {
    val fieldNames = rawFieldNames.map(unescapeFieldName)

    results.zipWithIndex.map { case (result, index) =>
      try {
        val fieldNameValueMap = fieldNames.zip(result).toMap

        val cacheItem = createPOJOFromMap(fieldNameValueMap)
        toItem(cacheItem)
      } catch {
        case e: Exception =>
          val message = s"Failed to create POJO from SQL result $index for $entityName"
          logger.error(message, e)
          throw new EdenaDataStoreException(message, e)
      }
    }
  }

  private def createPOJOFromMap(fieldNameValueMap: Map[String, Any]): CACHE_E = {
    import scala.reflect.runtime.universe._

    // Get the Java class for E_CACHE
    val runtimeClass = scala.reflect.runtime.currentMirror.runtimeClass(typeOf[CACHE_E])
    val javaClass = runtimeClass.asInstanceOf[Class[CACHE_E]]

    // Create instance using default constructor
    val instance = javaClass.getDeclaredConstructor().newInstance()

    // Set field values using setter methods
    fieldNameValueMap.foreach { case (fieldName, value) =>
      val setterName = s"set${fieldName.capitalize}"
      try {
        // Find setter method - try different parameter types
        val setterMethod = findSetterMethod(javaClass, setterName, value)
        if (setterMethod.isDefined) {
          val convertedValue =
            convertValueForSetter(value, setterMethod.get.getParameterTypes()(0))
          setterMethod.get.invoke(instance, convertedValue)
        } else {
          logger.warn(s"Could not find setter '$setterName' for field '$fieldName' for value class '${value.getClass.getName}'")
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error setting field '$fieldName' using setter '$setterName'. Skipping this field.", e)
      }
    }

    instance
  }

  private def findSetterMethod(
    clazz: Class[_],
    setterName: String,
    value: Any
  ): Option[java.lang.reflect.Method] = {
    try {
      // Try to find setter with the value's type first
      if (value != null) {
        val valueClass = value.getClass
        try {
          return Some(clazz.getMethod(setterName, valueClass))
        } catch {
          case _: NoSuchMethodException => // Try other approaches
        }
      }

      // Try common setter parameter types
      val commonTypes = Seq(
        classOf[String],
        classOf[java.lang.Integer],
        classOf[java.lang.Boolean],
        classOf[java.lang.Double],
        classOf[java.util.List[_]],
        classOf[java.util.Map[_, _]]
      )

      for (paramType <- commonTypes) {
        try {
          return Some(clazz.getMethod(setterName, paramType))
        } catch {
          case _: NoSuchMethodException => // Continue to next type
        }
      }
      None
    } catch {
      case _: Exception => None
    }
  }

  private def convertValueForSetter(
    value: Any,
    targetType: Class[_]
  ): Any = {
    if (value == null) return null

    try {
      targetType match {
        case t if t == classOf[String] => value.toString
        case t if t == classOf[java.lang.Integer] =>
          value match {
            case i: Int               => java.lang.Integer.valueOf(i)
            case i: java.lang.Integer => i
            case s: String            => java.lang.Integer.valueOf(s)
            case _                    => java.lang.Integer.valueOf(value.toString)
          }
        case t if t == classOf[java.lang.Boolean] =>
          value match {
            case b: Boolean           => java.lang.Boolean.valueOf(b)
            case b: java.lang.Boolean => b
            case s: String            => java.lang.Boolean.valueOf(s)
            case _                    => java.lang.Boolean.valueOf(value.toString)
          }
        case t if t == classOf[java.lang.Double] =>
          value match {
            case d: Double           => java.lang.Double.valueOf(d)
            case d: java.lang.Double => d
            case s: String           => java.lang.Double.valueOf(s)
            case _                   => java.lang.Double.valueOf(value.toString)
          }
        case _ => value // Return as-is for other types
      }
    } catch {
      case _: Exception => value // Fall back to original value
    }
  }
}