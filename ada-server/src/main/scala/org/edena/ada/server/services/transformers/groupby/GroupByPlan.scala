package org.edena.ada.server.services.transformers.groupby

import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldTypeFactory
import org.edena.ada.server.models.Field
import org.edena.ada.server.models.datatrans.{GroupByAggregation, GroupByFunction, GroupByOrder, GroupByTransformation}
import org.edena.ada.server.services.transformers.groupby.GroupByValues.{compare, toStoredJson, value, values}
import org.edena.core.field.FieldTypeId
import org.edena.store.json.JsObjectIdentity
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsValue}

import scala.collection.mutable
import org.edena.core.DefaultTypes.Seq

/**
  * A group-by transformation resolved against the source dictionary: the result fields and the computation of a
  * group's result row, see `apply`.
  *
  * @param keyFields the group-by fields (as in the source)
  * @param aggregations the aggregations, each with its out field
  * @param projection the source fields read (the group-by, order, in, distinct and condition fields)
  */
private[transformers] final class GroupByPlan(
  val keyFields: Seq[Field],
  orderBy: Seq[GroupByOrder],
  val aggregations: Seq[GroupByAggregator],
  val projection: Seq[String]
) {
  val keyFieldNames = keyFields.map(_.name)

  // the result dictionary
  val fields: Seq[Field] = keyFields ++ aggregations.map(_.field)

  // a defined value before an undefined one, either way
  private val rowOrdering: Ordering[JsObject] = new Ordering[JsObject] {
    override def compare(row1: JsObject, row2: JsObject) =
      orderBy.iterator.map { order =>
        (value(row1, order.fieldName), value(row2, order.fieldName)) match {
          case (JsNull, JsNull) => 0
          case (JsNull, _) => 1
          case (_, JsNull) => -1
          case (value1, value2) =>
            val result = GroupByValues.compare(value1, value2)
            if (order.desc) -result else result
        }
      }.find(_ != 0).getOrElse(0)
  }

  def keyOf(row: JsObject): Seq[JsValue] =
    keyFieldNames.map(value(row, _))

  /**
    * The result row of a group: the key values and the aggregated values. The rows are ordered by `orderBy`, keeping
    * the given order on a tie.
    */
  def apply(
    key: Seq[JsValue],
    rows: Seq[JsObject]
  ): JsObject = {
    val orderedRows = if (orderBy.nonEmpty) rows.sorted(rowOrdering) else rows

    JsObject(
      keyFieldNames.zip(key) ++
        aggregations.map(aggregation => (aggregation.field.name, aggregation(orderedRows)))
    )
  }
}

/**
  * An aggregation computing the value of its out field from a group's (ordered) rows.
  */
private[transformers] final class GroupByAggregator(
  val field: Field,
  compute: Seq[JsObject] => JsValue
) {
  def apply(rows: Seq[JsObject]): JsValue = compute(rows)
}

private[transformers] object GroupByPlan {

  import GroupByFunction.{Avg, Collect, CollectDistinct, Count, CountDistinct, First, Last, Max, Min, Mode, Priority, Sum}

  private val idName = JsObjectIdentity.name

  private val multiInFieldFunctions = Set(CountDistinct, Mode, Collect, CollectDistinct, Priority)
  private val orderedTypes = Set(FieldTypeId.Integer, FieldTypeId.Double, FieldTypeId.Date, FieldTypeId.String, FieldTypeId.Text)
  private val numericTypes = Set(FieldTypeId.Integer, FieldTypeId.Double)

  /**
    * @throws AdaException if the spec isn't valid for the dictionary (the message says why)
    */
  def apply(
    spec: GroupByTransformation,
    dictionary: Traversable[Field],
    ftf: FieldTypeFactory
  ): GroupByPlan = {
    val nameFieldMap = dictionary.map(field => (field.name, field)).toMap

    def fieldOf(name: String, usage: => String) =
      nameFieldMap.getOrElse(name, throw new AdaException(s"$usage: the field '$name' not found in the data set."))

    // group-by fields
    check(spec.groupByFieldNames.nonEmpty, "No group-by field given.")
    checkNoDuplicates("group-by field", spec.groupByFieldNames)

    val keyFields = spec.groupByFieldNames.map { name =>
      val field = fieldOf(name, "Group by")
      check(name != idName, s"Group by: '$idName' can't be a group-by field.")
      check(!field.isArray, s"Group by: the field '$name' is an array.")
      check(
        field.fieldType != FieldTypeId.Json && field.fieldType != FieldTypeId.Text,
        s"Group by: the field '$name' is a ${field.fieldType}, which can't be sorted."
      )
      field
    }

    // order
    spec.orderBy.foreach { order =>
      val field = fieldOf(order.fieldName, "Order by")
      check(!field.isArray && field.fieldType != FieldTypeId.Json, s"Order by: the field '${order.fieldName}' isn't a scalar.")
    }

    // aggregations
    check(spec.aggregations.nonEmpty, "No aggregation given.")
    val outFieldNames = spec.aggregations.map(_.outFieldName)
    checkNoDuplicates("out field", outFieldNames)

    val aggregators = spec.aggregations.map { aggregation =>
      val name = aggregation.outFieldName
      check(name.trim.nonEmpty, "An aggregation has no out field name.")
      check(name != idName, s"'$idName' can't be an out field.")
      check(!spec.groupByFieldNames.contains(name), s"The out field '$name' is a group-by field.")

      aggregator(aggregation, nameFieldMap, ftf)
    }

    val projection = (
      spec.groupByFieldNames ++
        spec.orderBy.map(_.fieldName) ++
        spec.aggregations.flatMap { aggregation =>
          aggregation.inFieldNames ++ aggregation.distinctFieldName ++ aggregation.conditions.map(_.fieldName)
        }
      ).distinct

    new GroupByPlan(keyFields, spec.orderBy, aggregators, projection)
  }

  private def aggregator(
    aggregation: GroupByAggregation,
    nameFieldMap: Map[String, Field],
    ftf: FieldTypeFactory
  ): GroupByAggregator = {
    val function = aggregation.function
    val usage = s"The aggregation '${aggregation.outFieldName}' ($function)"

    def fail(message: String) = throw new AdaException(s"$usage: $message")
    def checkIt(condition: Boolean, message: => String) = if (!condition) fail(message)

    val inFields = aggregation.inFieldNames.map { name =>
      nameFieldMap.getOrElse(name, fail(s"the in field '$name' not found in the data set."))
    }
    checkIt(aggregation.inFieldNames.distinct.size == aggregation.inFieldNames.size, "an in field repeats.")

    // in fields and options
    function match {
      case Count => checkIt(inFields.isEmpty, "takes no in field (a condition can restrict the rows).")
      case _ if multiInFieldFunctions.contains(function) => checkIt(inFields.nonEmpty, "needs an in field.")
      case _ => checkIt(inFields.size == 1, "needs exactly one in field.")
    }

    checkIt(!aggregation.skipNulls || function == First || function == Last, "skipNulls is for First / Last only.")
    checkIt(aggregation.ranking.isEmpty || function == Priority, "a ranking is for Priority only.")
    checkIt(function != Priority || aggregation.ranking.nonEmpty, "needs a ranking.")
    checkIt(aggregation.distinctFieldName.isEmpty || function == Mode, "distinctFieldName is for Mode only.")
    checkIt(aggregation.excludeValues.isEmpty || inFields.nonEmpty, "excludeValues needs an in field.")

    // the types of the in fields
    inFields.headOption.foreach { inField =>
      inFields.tail.foreach { otherField =>
        checkIt(
          otherField.fieldType == inField.fieldType && otherField.enumValues == inField.enumValues,
          s"the in fields '${inField.name}' and '${otherField.name}' have different types (or enum values)."
        )
      }

      val fieldType = inField.fieldType
      function match {
        case First | Last => ()
        case Min | Max => checkIt(orderedTypes.contains(fieldType), s"the in field is a $fieldType, not a number, date or string.")
        case Sum | Avg => checkIt(numericTypes.contains(fieldType), s"the in field is a $fieldType, not a number.")
        case _ => checkIt(fieldType != FieldTypeId.Json, "the in field is a Json.")
      }
    }

    val inFieldNames = aggregation.inFieldNames
    val matches = GroupByConditions(aggregation.conditions, nameFieldMap, ftf, usage)

    def toJsons(texts: Seq[String], kind: String) =
      texts.map(text => toStoredJson(inFields.head, text, ftf, s"$usage, $kind"))

    val excluded = toJsons(aggregation.excludeValues, "excludeValues").toSet

    // the values of the in fields (elements), not excluded
    def inValues(row: JsObject): Seq[JsValue] =
      inFieldNames.flatMap(values(row, _)).filterNot(excluded.contains)

    // the whole value of the in field (an array without the excluded elements), null if undefined
    def wholeValue(row: JsObject): JsValue =
      value(row, inFieldNames.head) match {
        case JsArray(elements) =>
          val kept = elements.filter(element => element != JsNull && !excluded.contains(element))
          if (kept.isEmpty) JsNull else JsArray(kept)
        case value if excluded.contains(value) => JsNull
        case value => value
      }

    def allInValues(rows: Seq[JsObject]) = rows.flatMap(inValues)

    def first(rows: Seq[JsObject]): JsValue =
      if (aggregation.skipNulls) rows.iterator.map(wholeValue).find(_ != JsNull).getOrElse(JsNull)
      else rows.headOption.map(wholeValue).getOrElse(JsNull)

    val distinctFieldName = aggregation.distinctFieldName.map { name =>
      val field = nameFieldMap.getOrElse(name, fail(s"the distinct field '$name' not found in the data set."))
      checkIt(!field.isArray && field.fieldType != FieldTypeId.Json, s"the distinct field '$name' isn't a scalar.")
      name
    }

    val compute: Seq[JsObject] => JsValue = function match {
      case Count => rows => JsNumber(rows.size)

      case CountDistinct => rows => JsNumber(allInValues(rows).distinct.size)

      case Min => rows => allInValues(rows).reduceOption((value1, value2) => if (compare(value2, value1) < 0) value2 else value1).getOrElse(JsNull)

      case Max => rows => allInValues(rows).reduceOption((value1, value2) => if (compare(value2, value1) > 0) value2 else value1).getOrElse(JsNull)

      case First => first

      case Last => rows => first(rows.reverse)

      case Mode => rows => mode(rows, inValues, distinctFieldName)

      case Collect => rows =>
        val values = allInValues(rows)
        if (values.isEmpty) JsNull else JsArray(values)

      case CollectDistinct => rows =>
        val distinctValues = allInValues(rows).distinct
        if (distinctValues.isEmpty) JsNull else JsArray(distinctValues)

      case Priority =>
        val ranking = toJsons(aggregation.ranking, "ranking")
        val rankMap = ranking.zipWithIndex.reverse.toMap // the first rank of a repeated value

        rows => {
          val ranks = allInValues(rows).flatMap(rankMap.get)
          if (ranks.isEmpty) JsNull else ranking(ranks.min)
        }

      case Sum => rows =>
        val numbers = allInValues(rows).collect { case JsNumber(number) => number }
        if (numbers.isEmpty) JsNull else JsNumber(numbers.sum)

      case Avg => rows =>
        val numbers = allInValues(rows).collect { case JsNumber(number) => number }
        if (numbers.isEmpty) JsNull else JsNumber(BigDecimal((numbers.sum / numbers.size).toDouble))
    }

    new GroupByAggregator(
      outField(aggregation, inFields.headOption),
      rows => compute(rows.filter(matches))
    )
  }

  // the most frequent value (counted once per distinct value of the distinct field, if any), the first one on a tie
  private def mode(
    rows: Seq[JsObject],
    inValues: JsObject => Seq[JsValue],
    distinctFieldName: Option[String]
  ): JsValue = {
    // insertion-ordered, i.e. by the first occurrence
    val valueCounts = mutable.LinkedHashMap[JsValue, Int]()
    val countedValueKeys = mutable.Set[(JsValue, JsValue)]()

    rows.foreach { row =>
      val distinctKey = distinctFieldName.map(value(row, _))

      inValues(row).distinct.foreach { inValue =>
        val counted = distinctKey.forall(key => countedValueKeys.add((inValue, key)))
        val count = valueCounts.getOrElse(inValue, 0)
        valueCounts.update(inValue, if (counted) count + 1 else count)
      }
    }

    valueCounts.foldLeft(Option.empty[(JsValue, Int)]) { case (best, (value, count)) =>
      if (best.forall(_._2 < count)) Some((value, count)) else best
    }.map(_._1).getOrElse(JsNull)
  }

  private def outField(
    aggregation: GroupByAggregation,
    inField: Option[Field]
  ): Field = {
    // the in field's type and display settings
    def ofInType(isArray: Boolean) =
      inField.get.copy(name = aggregation.outFieldName, isArray = isArray, aliases = Nil, categoryId = None, category = None)

    val field = aggregation.function match {
      case Count | CountDistinct => Field(aggregation.outFieldName, fieldType = FieldTypeId.Integer)
      case Avg => Field(aggregation.outFieldName, fieldType = FieldTypeId.Double)
      case Collect | CollectDistinct => ofInType(isArray = true)
      case First | Last => ofInType(isArray = inField.get.isArray)
      case _ => ofInType(isArray = false)
    }

    field.copy(label = aggregation.label.map(_.trim).filter(_.nonEmpty))
  }

  private def checkNoDuplicates(kind: String, names: Seq[String]): Unit = {
    val duplicateNames = names.diff(names.distinct).distinct
    check(duplicateNames.isEmpty, s"Duplicate $kind(s): ${duplicateNames.mkString(", ")}.")
  }

  private def check(condition: Boolean, message: => String): Unit =
    if (!condition) throw new AdaException(message)
}
