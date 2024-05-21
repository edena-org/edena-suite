package org.edena.store.elastic.caseclass

import com.sksamuel.elastic4s.Indexable
import org.edena.store.elastic.ElasticSerializer
import org.edena.core.util.ReflectionUtil._

import scala.collection.JavaConversions._
import scala.reflect.ClassTag
import java.util.{Date, TimeZone, UUID}
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import com.sksamuel.exts.Logging
import org.apache.commons.lang3.StringEscapeUtils
import org.edena.core.store.EdenaDataStoreException
import org.edena.core.field.FieldHelper
import org.edena.core.util.DynamicConstructor

trait ElasticCaseClassSerializer[E] extends ElasticSerializer[E] with HasDynamicConstructor[E] with FieldHelper {

  logging: Logging =>

  protected implicit val classTag: ClassTag[E]

  private val members = getCaseClassMemberMethods[E]
  protected val namedFieldTypes = caseClassToFlatFieldTypes[E]()

  protected val arrayFieldNames = namedFieldTypes.filter(_._2.isArray).map(_._1).toSet
  protected def isArray(fieldName: String) = arrayFieldNames.contains(fieldName)

  private val treatConcreteClassName = false
  private val concreteClassFieldName = "concreteClass"

  // override if a special json serialization is needed
  protected implicit val indexable = new Indexable[E] {
    def json(t: E) = {

      val jsonString = getFieldNamesAndValues(t, members).flatMap {
        case (fieldName, value) =>
          valueToJsonString(value).map(jsonValue =>
            "  \"" + fieldName + "\": " + jsonValue
          )
      }.mkString(", ")

      s"{${jsonString}}"
    }
  }

  // primitive conversion of any value to a json... override if needed
  protected def valueToJsonString(value: Any): Option[String] =
    value match {
      case None => None
      case Some(x) => valueToJsonString(x)
      case _ =>
        val x: String = value match {
          case string: String =>
            "\"" + StringEscapeUtils.escapeJava(string) + "\""

          case date: Date =>
            date.getTime.toString

          case uuid: UUID =>
            "\"" + uuid.toString + "\""

          case x: Traversable[_] =>
            "[" + x.flatMap(valueToJsonString).mkString(",") + "]"

          case _ => value.toString
        }

        Some(x)
    }

  // get result
  override protected def serializeGetResult(response: GetResponse): Option[E] = {
    if (response.exists) {
      val nonNullMap = removeNulls(response.sourceAsMap)
      val constructor = findConstructor(nonNullMap)
      val item = constructor.applyOrException(nonNullMap)
      Some(item)
    } else
      None
  }

  // full search result (i.e. no projection)
  override protected def serializeSearchResult(
    response: SearchResponse
  ): Traversable[E] =
    if (treatConcreteClassName) {
      // TODO: to make it more optimal we should group the results by a concrete class field name and create a constructor for each group
      super.serializeSearchResult(response)
    } else {
      response.hits.hits.toTraversable match {
        case Nil => Nil

        case hits =>
          val constructor = findConstructor(hits.head.sourceAsMap)

          hits.map { hit =>
            val nonNullMap = removeNulls(hit.sourceAsMap)
            constructor.applyOrException(nonNullMap)
          }
      }
    }

  // full search hit (i.e. no projection)
  override protected def serializeSearchHit(
    result: SearchHit
  ): E = {
    val nonNullMap = removeNulls(result.sourceAsMap)
    val constructor = findConstructor(nonNullMap)
    constructor.applyOrException(nonNullMap)
  }

  /////////////////////
  // PROJECTION FUNS //
  /////////////////////

  override protected def serializeProjectionFieldMap(
    projection: Seq[String],
    fieldMap: Map[String, Any]
  ) = {
    val valueMap = fieldsToValueMap(fieldMap)

    val constructor = findConstructor(valueMap)
    constructor.applyOrException(valueMap)
  }

  override protected def serializeProjectionSearchHits(
    projection: Seq[String],
    results: Array[SearchHit]
  ): Traversable[E] =
    if (treatConcreteClassName) {
      // TODO: to make it more optimal we should group the results by a concrete class field name and create a constructor for each group
      super.serializeProjectionSearchHits(projection, results)
    } else {
      results.toTraversable match {
        case Nil => Nil

        case results =>
          val firstFields = getFieldsSafe(results.head)
          val constructor = findConstructor(firstFields) // TODO: use a projection to find a constructor here

          results.map { result =>
            val valueMap = searchHitToValueMap(result)

            constructor.applyOrException(valueMap)
          }
      }
    }

  override protected def fieldsToValueMap(fields: Map[String, Any]): Map[String, Any] =
    fields.map { case (fieldName, value) =>

      val finalValue =
        if (!isArray(fieldName))
          value match {
            case list: List[_] => list.head
            case _ => value
          }
        else
          value

      (fieldName, finalValue)
    }

  protected def removeNulls(valueMap: Map[String, Any]) = valueMap.filter(_._2 != null)

  protected implicit class ConstructorExt[E](constructor: DynamicConstructor[E]) {

    def applyOrException(valueMap: Map[String, Any]) =
      constructor(valueMap).getOrElse(
        throw new EdenaDataStoreException(s"Constructor for the values ${valueMap.mkString(", ")} failed.")
      )
  }

  protected def findConstructor(valueMap: Map[String, Any]) =
    if (treatConcreteClassName) {
      val concreteClassName = valueMap.get(concreteClassFieldName).map(_.asInstanceOf[String])
      constructorOrException(valueMap, concreteClassName)
    } else
      constructorOrException(valueMap, None)
}