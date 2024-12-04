package org.edena.json.util

import org.edena.core.util.ReflectionUtil.{getCaseClassMemberNamesAndTypes, isCaseClass}
import play.api.libs.json.{JsArray, JsBoolean, JsDefined, JsNull, JsNumber, JsObject, JsReadable, JsString, JsUndefined, JsValue, Json}

import java.{util => ju}
import java.util.Date
import java.util.regex.Pattern
import scala.reflect.runtime.universe._

trait JsonHelper {

  private val optionType = typeOf[Option[_]]

  def jsonsToCsv(
    items: Traversable[JsObject],
    delimiter: String = ",",
    eol: String = "\n",
    explicitFieldNames: Seq[String] = Nil,
    replacements: Traversable[(String, String)] = Nil
  ) = {
    val sb = new StringBuilder(10000)

    if (items.nonEmpty || explicitFieldNames.nonEmpty) {

      // if field names are not explicitly provided use the fields of the first json
      val headerFieldNames = explicitFieldNames match {
        case Nil => items.head.fields.map(_._1)
        case _ => explicitFieldNames
      }

      // create a header
      def headerFieldName(fieldName: String) = replaceAll(replacements)(fieldName)

      val header = headerFieldNames.map(headerFieldName).mkString(delimiter)
      sb.append(header + eol)

      // transform each json to a delimited string and add to a buffer
      items.foreach { item =>
        val row = jsonToDelimitedString(item, headerFieldNames, delimiter, replacements)
        sb.append(row + eol)
      }
    }

    sb.toString
  }

  def jsonToDelimitedString(
    json: JsObject,
    fieldNames: Traversable[String],
    delimiter: String = ",",
    replacements: Traversable[(String, String)] = Nil
  ): String = {
    val replaceAllAux = replaceAll(replacements) _
    val itemFieldNameValueMap = json.fields.toMap

    fieldNames.map { fieldName =>
      itemFieldNameValueMap.get(fieldName).fold("") { jsValue =>
        jsValue match {
          case JsNull => ""
          case _: JsString => replaceAllAux(jsValue.as[String])
          case _ => jsValue.toString()
        }
      }
    }.mkString(delimiter)
  }

  def traverse(json: JsObject, path: String): Seq[JsValue] = {
    // helper function to extract JS values from an JS object
    def extractJsValues(jsObject: JsObject, fieldName: String) =
      (jsObject \ fieldName).toOption.map(jsValue =>
        jsValue match {
          case x: JsArray => x.value
          case _ => Seq(jsValue)
        }
      )

    path.split('.').foldLeft(Seq(json: JsValue)) {
      case (jsons: Seq[JsValue], fieldName) =>
        jsons.map { json =>
          json match {
            case x: JsObject => extractJsValues(x, fieldName)
            case _ => None
          }
        }.flatten.flatten
    }
  }

  def flatten(
    json: JsObject,
    delimiter: String = ".",
    excludedFieldNames: Set[String] = Set(),
    prefix: Option[String] = None
  ): JsObject =
    json.fields.foldLeft(Json.obj()) {
      case (acc, (fieldName, v)) =>
        val newPrefix = prefix.map(prefix => s"$prefix$delimiter$fieldName").getOrElse(fieldName)
        if (excludedFieldNames.contains(fieldName)) {
          acc + (newPrefix -> v)
        } else {
          v match {
            case jsObject: JsObject => acc.deepMerge(flatten(jsObject, delimiter, excludedFieldNames, Some(newPrefix)))
            case _ => acc + (newPrefix -> v)
          }
        }
    }

  def deflattenByType[T: TypeTag](
    json: JsObject,
    delimiter: String = ".",
    excludedFieldNames: Set[String] = Set()
  ): JsObject = {

    // helper function to extract prefix and non-prefix fields
    def deflattenFields(
      typ: Type,
      fields: Traversable[(String, JsValue)]
    ): Seq[(String, JsValue)] = {
      val fieldNameTypes = getCaseClassMemberNamesAndTypes(typ)
      val fieldNameValueMap = fields.toMap

      fieldNameTypes.map { case (fieldName, typ) =>
        val isOption = typ <:< optionType
        val actualType = if (isOption) typ.typeArgs.head else typ

        val jsValue = if (isCaseClass(actualType)) {
          val prefix = fieldName + delimiter
          val innerFields = fields.filter { case (jsFieldName, _) => jsFieldName.startsWith(prefix) }
          val innerFieldsStripped = innerFields.map { case (jsFieldName, value) => (jsFieldName.stripPrefix(prefix), value) }

          if (isOption && innerFieldsStripped.isEmpty) JsNull else JsObject(deflattenFields(actualType, innerFieldsStripped))
        } else {
          fieldNameValueMap.get(fieldName).getOrElse(JsNull)
        }
        (fieldName, jsValue)
      }.toSeq
    }

    val fieldNameValues = json.fields.map { fieldNameValue =>
      if (excludedFieldNames.contains(fieldNameValue._1))
        Left(fieldNameValue)
      else
        Right(fieldNameValue)
    }

    val excludedFieldNameValues = fieldNameValues.collect { case Left(l) => l }
    val normalFieldNameValues = fieldNameValues.collect { case Right(r) => r }

    JsObject(
      deflattenFields(typeOf[T], normalFieldNameValues) ++ excludedFieldNameValues
    )
  }

  def deflatten(
    json: JsObject,
    delimiter: String = "."
  ): JsObject = {

    // helper function to extract prefix and non-prefix fields
    def deflattenFields(
      fields: Seq[(String, JsValue)]
    ): Seq[(String, JsValue)] = {
      val prefixNonPrefixFields = fields.map { case (fieldName, jsValue) =>
        val prefixRest = fieldName.split(Pattern.quote(delimiter), 2)
        if (prefixRest.length < 2)
          Right((fieldName, jsValue))
        else
          Left((prefixRest(0), (prefixRest(1), jsValue)))
      }

      val prefixFields = prefixNonPrefixFields.flatMap(_.left.toOption)
      val nonPrefixFields = prefixNonPrefixFields.flatMap(_.right.toOption)

      val deflattedPrefixFields = prefixFields.groupBy(_._1).map { case (prefix, fields) =>
        val newFields = deflattenFields(fields.map(_._2))
        (prefix, JsObject(newFields))
      }

      nonPrefixFields ++ deflattedPrefixFields
    }

    JsObject(deflattenFields(json.fields))
  }

  private def replaceAll(
    replacements: Traversable[(String, String)])(
    value: String
  ) =
    replacements.foldLeft(value) { case (string, (from, to)) => string.replaceAll(from, to) }

  def filterAndSort(items: Seq[JsObject], orderBy: String, filter: String, filterFieldName: String) = {
    val filteredItems = if (filter.isEmpty) {
      items
    } else {
      val f = (filter + ".*").r
      items.filter { item =>
        val v = (item \ filterFieldName)
        f.unapplySeq(v.asOpt[String].getOrElse(v.toString())).isDefined
      }
    }

    val orderByField = if (orderBy.startsWith("-")) orderBy.substring(1) else orderBy

    val sortedItems = filteredItems.sortBy { item =>
      val v = (item \ orderByField)
      v.asOpt[String].getOrElse(v.toString())
    }

    if (orderBy.startsWith("-"))
      sortedItems.reverse
    else
      sortedItems
  }

  /**
   * Find items in field exactly matching input value.
   *
   * @param items           Input items.
   * @param value           Value for matching.
   * @param filterFieldName Field to be queried for value.
   * @return Found items.
   */
  def findBy(items: Seq[JsObject], value: String, filterFieldName: String) =
    items.filter { item =>
      val v = (item \ filterFieldName)
      v.asOpt[String].getOrElse(v.toString()).equals(value)
    }

  /**
   * Retrieve all items of specified field.
   *
   * @param items     Input items.
   * @param fieldName Field of interest.
   * @return Items in specified field.
   */
  def project(items: Traversable[JsObject], fieldName: String): Traversable[JsReadable] =
    items.map { item => (item \ fieldName) }

  def toString(value: JsReadable): Option[String] =
    value match {
      case JsNull => None
      case _: JsUndefined => None
      case JsString(s) => Some(s.trim)
      case JsNumber(n) => Some(n.toString)
      case JsBoolean(s) => Some(s.toString)
      case JsArray(s) => Some(s.flatMap(toString(_)).mkString(", "))
      case JsDefined(json) => toString(json)
//      case x: JsObject =>
//        val fieldsAsString = x.fields.map { case (fieldName, jsValue) => s"$fieldName: ${toString(jsValue)}"}
//        Some(fieldsAsString.mkString(", "))
      case _ => Some(value.toString)
    }

  def getValueFromJson(jsValue: JsValue): Any =
    jsValue match {
      case JsNull => null
      case JsString(value) => value
      case JsNumber(value) => value
      case JsBoolean(value) => value
      case JsArray(value) => value
      case x: JsObject => x
    }

  // from mongo
  protected def toValue(jsValue: JsValue): Option[Any] =
    jsValue match {
      case JsNull => None
      case JsString(value) => Some(value)
      case JsNumber(value) => Some(value)
      case JsBoolean(value) => Some(value)
      case JsArray(value) =>
        val seq = value.seq.map(toValue)
        Some(seq)

      case jsObject: JsObject =>
        Some(toValueMap(jsObject))
    }

  protected def toValueMap(jsObject: JsObject): Map[String, Option[Any]] =
    jsObject.value.map { case (fieldName, jsValue) =>
      (fieldName, toValue(jsValue))
    }.toMap

  def toJson(value: Any): JsValue =
    if (value == null)
      JsNull
    else
      value match {
        case x: JsValue => x // nothing to do
        case x: String => JsString(x)
        case x: BigDecimal => JsNumber(x)
        case x: Integer => JsNumber(BigDecimal.valueOf(x.toLong))
        case x: Long => JsNumber(BigDecimal.valueOf(x))
        case x: Double => JsNumber(BigDecimal.valueOf(x))
        case x: Float => JsNumber(BigDecimal.valueOf(x.toDouble))
        case x: Boolean => JsBoolean(x)
        case x: ju.Date => Json.toJson(x)
        case x: Option[_] => x.map(toJson).getOrElse(JsNull)
        case x: Array[_] => JsArray(x.map(toJson))
        case x: Seq[_] => JsArray(x.map(toJson))
        case _ => throw new IllegalArgumentException(s"No JSON formatter found for the class ${value.getClass.getName}.")
      }

  /**
   * Count objects of specified field to which the filter applies.
   *
   * @param items           Json input items.
   * @param filter          Filter string.
   * @param filterFieldName Name of he fields to be filtered.
   * @return Number of items to which the filter applies.
   */
  def count(items: Seq[JsObject], filter: String, filterFieldName: String): Int = {
    val filteredItems = if (filter.isEmpty) {
      items
    } else {
      val f = (filter + ".*").r
      items.filter { item =>
        val v = (item \ filterFieldName)
        f.unapplySeq(v.asOpt[String].getOrElse(v.toString())).isDefined
      }
    }
    filteredItems.length
  }
}

object JsonUtil extends JsonHelper