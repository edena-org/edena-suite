package org.edena.ada.server.dataaccess

import scala.collection.mutable.{Map => MMap}
import org.edena.ada.server.dataaccess.StoreTypes.FieldStore
import org.edena.ada.server.models.Field
import org.edena.core.store.ValueMapAux.ValueMap
import org.edena.core.store._
import org.edena.core.field.FieldTypeId
import org.edena.core.util.ReflectionUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// important: this store doesn't persist changes to the underlying fields (hence the "transient" adjective)
private class TransientLocalFieldStore(fields: Seq[Field]) extends FieldStore {

  private val fieldMap = MMap[String, Field](fields.map(field => (field.name, field)):_*)

  override def update(entity: Field) = Future {
    fieldMap.update(entity.name, entity)
    entity.name
  }

  override def delete(id: String) =
    Future(fieldMap.remove(id))

  override def deleteAll =
    Future(fieldMap.clear)

  override def save(entity: Field) =
    Future {
      fieldMap.put(entity.name, entity); entity.name
    }

  override def get(id: String) =
    Future(fieldMap.get(id))

  private def extractValue(
    field: Field,
    fieldName: String
  ): Any =
    fieldName match {
      case "name" => field.name
      case "label" => field.label
      case "fieldType" => field.fieldType
      case "isArray" => field.isArray
      case _ => throw new EdenaDataStoreException(s"Filtering of field's field ${fieldName} unsupported.")
    }

  // projection is ignored
  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = Future {
    // filter by criteria
    val filteredFields =
      fieldMap.values.filter { field =>
        matches(criterion, field)
      }

    def compareFields(field1: Field, field2: Field, sort: Sort): Int = {
      val value1 = extractValue(field1, sort.fieldName).asInstanceOf[Comparable[Any]]
      val value2 = extractValue(field2, sort.fieldName).asInstanceOf[Comparable[Any]]
      sort match {
        case AscSort(_) => value1.compareTo(value2)
        case DescSort(_) => -value1.compareTo(value2)
      }
    }

    def sortBy(fieldsSeq: Seq[Field], sort: Sort): Seq[Field] =
      fieldsSeq.sortWith { case (field1, field2) =>
        compareFields(field1, field2, sort) < 0
      }

    val filteredFieldsSeq = filteredFields.toSeq.map(field => (field, true))

    // sort
    val sortedEqFields: Seq[(Field, Boolean)] =
      sort.foldLeft(filteredFieldsSeq) { case (result, sort) =>
        val indeces = result.zipWithIndex.filter(!_._1._2).map(_._2)
        val fields = result.map(_._1)
        val (_, processed, tail) = indeces.foldLeft((0, Seq[Seq[Field]](), fields)) {
          case ((size, processed, todo), index) =>
            val newSplit = todo.splitAt(index - size)
            (size + newSplit._1.size, processed ++ Seq(newSplit._1), newSplit._2)
        }

        val sorted: Seq[Field] = (processed ++ Seq(tail)).map(sortBy(_, sort)).fold(Nil)(_ ++ _)

        val equals = sorted.zip(sorted.tail).map { case (field1, field2) => compareFields(field1, field2, sort) == 0}
        sorted.zip(Seq(true) ++ equals)
      }

    val sortedFields = sortedEqFields.map(_._1)

    // skip
    val skippedFields = skip.map(sortedFields.drop).getOrElse(sortedFields)

    // limit
    limit.map(skippedFields.take).getOrElse(skippedFields)
  }

  private def matches(criterion: Criterion, field: Field): Boolean =
    criterion match {
      case c: And =>
        c.criteria.forall(matches(_, field))

      case c: Or =>
        c.criteria.exists(matches(_, field))

      case NoCriterion =>
        true

      case c: ValueCriterion[_] =>
        val value = extractValue(field, c.fieldName)
        matches(c, value)
    }

  // since projection is ignored just delegates to the normal find function and decompose to field-values
  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[ValueMap]] = {
    find(criterion, sort, projection, limit, skip).map {
      _.map { field =>
        Map(
          "name" -> Some(field.name),
          "label" -> field.label,
          "fieldType" -> Some(field.fieldType),
          "isArray" -> Some(field.isArray),
          "enumValues" -> Some(field.enumValues),
          "displayDecimalPlaces" -> Some(field.displayDecimalPlaces),
          "displayTrueValue" -> Some(field.displayTrueValue),
          "displayFalseValue" -> Some(field.displayFalseValue),
          "aliases" -> Some(field.aliases)
        )
      }
    }
  }

  override def count(criterion: Criterion) = Future {
    // filter by criteria
    val filteredFields =
      fieldMap.values.filter { field =>
        matches(criterion, field)
      }

    filteredFields.size
  }

  private def matches[T](
    criterion: ValueCriterion[T],
    value: T
  ): Boolean =
    criterion match {
      case c: EqualsCriterion[T] => c.value.equals(value)

      case c: EqualsNullCriterion => value == null || value == None

      case c: RegexEqualsCriterion => value.equals(c.value) // TODO

      case c: NotEqualsCriterion[T] => !c.value.equals(value)

      case c: NotEqualsNullCriterion => value != null && value != None

      case c: InCriterion[T] => c.value.contains(value)

      case c: NotInCriterion[T] => !c.value.contains(value)

      case c: GreaterCriterion[T] => value.toString.toDouble > c.value.toString.toDouble // TODO

      case c: GreaterEqualCriterion[T] => value.toString.toDouble >= c.value.toString.toDouble // TODO

      case c: LessCriterion[T] => value.toString.toDouble < c.value.toString.toDouble // TODO

      case c: LessEqualCriterion[T] => value.toString.toDouble <= c.value.toString.toDouble // TODO
    }

  // essentially no-op
  override def flushOps: Future[Unit] = Future(())
}

object TransientLocalFieldStore {
  def apply(fields: Seq[Field]): FieldStore = new TransientLocalFieldStore(fields)
}