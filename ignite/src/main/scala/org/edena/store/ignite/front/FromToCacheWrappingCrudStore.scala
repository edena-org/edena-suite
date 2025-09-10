package org.edena.store.ignite.front

import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.Identity
import org.edena.core.store._
import org.edena.core.util.DynamicConstructorFinder
import org.edena.core.DefaultTypes.Seq

import scala.reflect.runtime.universe.TypeTag
import scala.reflect.{ClassTag, classTag}
import java.lang.reflect.{Method, Constructor}

class FromToCacheWrappingCrudStore[ID, E, E_CACHE: TypeTag](
  cache: IgniteCache[ID, E_CACHE],
  entityName: String,
  val ignite: Ignite,
  identity: Identity[E, ID],
  usePOJOAccess: Boolean = false
)(
  fromCache: E_CACHE => E,
  toCache: E => E_CACHE
) extends AbstractCacheWrappingCrudStore[ID, E, ID, E_CACHE](cache, entityName, identity) {

//  private val frontItemConstructorFinder = DynamicConstructorFinder.apply[E]

  private val cacheItemConstructorFinder = DynamicConstructorFinder.apply[E_CACHE]

  // hooks
  override def toCacheId(id: ID): ID = id

  override def toItem(cacheItem: E_CACHE): E = fromCache(cacheItem)

  override def toCacheItem(item: E): E_CACHE = toCache(item)

  override def findResultToItem(result: Traversable[(String, Any)]): E = {
    if (usePOJOAccess) {
      findResultToItemPOJO(result)
    } else {
      println(s"findResultToItem ($entityName): ${result.mkString(", ")}")

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
    println(s"findResultToItemPOJO ($entityName): ${result.mkString(", ")}")

    try {
      val fieldNameValueMap = result.map { case (fieldName, value) =>
        val cleanFieldName = unescapeFieldName(fieldName)
        println(s"Processing field: '$fieldName' -> '$cleanFieldName' = $value")
        (cleanFieldName, value)
      }.toMap

      val cacheItem = createPOJOFromMap(fieldNameValueMap)
      val domainItem = toItem(cacheItem)
      println(s"Successfully converted POJO to domain object: $domainItem")
      domainItem
    } catch {
      case e: Exception =>
        println(s"Error in findResultToItemPOJO for $entityName: ${e.getMessage}")
        e.printStackTrace()
        throw new EdenaDataStoreException(s"Failed to create POJO from SQL result for $entityName", e)
    }
  }

  private def findResultsToItemsPOJO(
    rawFieldNames: Seq[String],
    results: Traversable[Seq[Any]]
  ): Traversable[E] = {
    val fieldNames = rawFieldNames.map(unescapeFieldName)
    println(s"findResultsToItemsPOJO ($entityName): Processing ${results.size} results with fields: ${fieldNames.mkString(", ")}")

    results.zipWithIndex.map { case (result, index) =>
      try {
        println(s"Processing result $index: ${result}")
        val fieldNameValueMap = fieldNames.zip(result).toMap
        println(s"Field mapping for result $index: $fieldNameValueMap")
        
        val cacheItem = createPOJOFromMap(fieldNameValueMap)
        val domainItem = toItem(cacheItem)
        println(s"Successfully processed result $index")
        domainItem
      } catch {
        case e: Exception =>
          println(s"Error processing result $index for $entityName: ${e.getMessage}")
          e.printStackTrace()
          throw new EdenaDataStoreException(s"Failed to create POJO from SQL result $index for $entityName", e)
      }
    }
  }

  private def createPOJOFromMap(fieldNameValueMap: Map[String, Any]): E_CACHE = {
    import scala.reflect.runtime.universe._
    
    // Get the Java class for E_CACHE
    val runtimeClass = scala.reflect.runtime.currentMirror.runtimeClass(typeOf[E_CACHE])
    val javaClass = runtimeClass.asInstanceOf[Class[E_CACHE]]
    
    println(s"Creating POJO of type: ${javaClass.getName}")
    println(s"Field values: ${fieldNameValueMap}")
    
    // Create instance using default constructor
    val instance = javaClass.getDeclaredConstructor().newInstance()
    
    // Set field values using setter methods
    fieldNameValueMap.foreach { case (fieldName, value) =>
      val setterName = s"set${fieldName.capitalize}"
      try {
        // Find setter method - try different parameter types
        val setterMethod = findSetterMethod(javaClass, setterName, value)
        if (setterMethod.isDefined) {
          val convertedValue = convertValueForSetter(value, setterMethod.get.getParameterTypes()(0))
          setterMethod.get.invoke(instance, convertedValue)
          println(s"Successfully set $fieldName = $convertedValue using $setterName")
        } else {
          println(s"Warning: Could not find setter '$setterName' for field '$fieldName'")
        }
      } catch {
        case e: Exception =>
          println(s"Error setting field '$fieldName' using setter '$setterName': ${e.getMessage}")
      }
    }

    instance
  }
  
  private def findSetterMethod(clazz: Class[_], setterName: String, value: Any): Option[java.lang.reflect.Method] = {
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
  
  private def convertValueForSetter(value: Any, targetType: Class[_]): Any = {
    if (value == null) return null
    
    try {
      targetType match {
        case t if t == classOf[String] => value.toString
        case t if t == classOf[java.lang.Integer] => 
          value match {
            case i: Int => java.lang.Integer.valueOf(i)
            case i: java.lang.Integer => i
            case s: String => java.lang.Integer.valueOf(s)
            case _ => java.lang.Integer.valueOf(value.toString)
          }
        case t if t == classOf[java.lang.Boolean] =>
          value match {
            case b: Boolean => java.lang.Boolean.valueOf(b)
            case b: java.lang.Boolean => b
            case s: String => java.lang.Boolean.valueOf(s)
            case _ => java.lang.Boolean.valueOf(value.toString)
          }
        case t if t == classOf[java.lang.Double] =>
          value match {
            case d: Double => java.lang.Double.valueOf(d)
            case d: java.lang.Double => d
            case s: String => java.lang.Double.valueOf(s)
            case _ => java.lang.Double.valueOf(value.toString)
          }
        case _ => value // Return as-is for other types
      }
    } catch {
      case _: Exception => value // Fall back to original value
    }
  }
}
