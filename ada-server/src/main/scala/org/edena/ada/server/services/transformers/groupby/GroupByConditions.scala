package org.edena.ada.server.services.transformers.groupby

import java.util.regex.{Pattern, PatternSyntaxException}

import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldTypeFactory
import org.edena.ada.server.models.Field
import org.edena.ada.server.services.transformers.groupby.GroupByValues.{compare, toStoredJson, values}
import org.edena.core.field.FieldTypeId
import org.edena.core.{ConditionType, FilterCondition}
import play.api.libs.json.{JsNumber, JsObject, JsString, JsValue}

import org.edena.core.DefaultTypes.Seq

/**
  * Filter conditions evaluated on (stored) rows, as the stores evaluate them. On an array field `=`, `in`, `>`, ...
  * mean that an element matches, `!=` and `nin` that none does; `like` means that the regex is found in a string value,
  * `contains` that all the words occur (case-insensitively).
  */
private[transformers] object GroupByConditions {

  import ConditionType.{ContainsWords, Equals, Greater, GreaterEqual, In, IsDefined, IsUndefined, Less, LessEqual, NotEquals, NotIn, RegexEquals, RegexNotEquals}

  /**
    * A predicate on rows matching all the conditions.
    *
    * @throws AdaException if a condition's field isn't in the dictionary, is a Json, or a value isn't valid
    */
  def apply(
    conditions: Seq[FilterCondition],
    nameFieldMap: Map[String, Field],
    ftf: FieldTypeFactory,
    usage: => String
  ): JsObject => Boolean = {
    val predicates = conditions.flatMap(predicate(_, nameFieldMap, ftf, usage))
    row => predicates.forall(_(row))
  }

  private def predicate(
    condition: FilterCondition,
    nameFieldMap: Map[String, Field],
    ftf: FieldTypeFactory,
    usage: => String
  ): Option[JsObject => Boolean] = {
    val name = condition.fieldName
    val field = nameFieldMap.getOrElse(name,
      throw new AdaException(s"$usage: the condition field '$name' not found in the data set.")
    )

    if (field.fieldType == FieldTypeId.Json)
      throw new AdaException(s"$usage: the condition field '$name' is a Json.")

    def toJson(text: String) = toStoredJson(field, text, ftf, usage)

    val text = condition.value.map(_.trim).filter(_.nonEmpty)
    def jsons = text.map(_.split(",").toSeq.map(_.trim).filter(_.nonEmpty).map(toJson)).getOrElse(Nil).toSet

    def regex = text.map { text =>
      try {
        Pattern.compile(text)
      } catch {
        case e: PatternSyntaxException => throw new AdaException(s"$usage: the regex '$text' isn't valid: ${e.getMessage}")
      }
    }

    // with no value a comparison or a regex makes no condition, as in a filter
    def comparing(accept: Int => Boolean) = text.map(toJson).map { json =>
      (row: JsObject) => values(row, name).exists(value => comparable(value, json) && accept(compare(value, json)))
    }

    def finding(pattern: Pattern) = (row: JsObject) =>
      values(row, name).exists {
        case JsString(string) => pattern.matcher(string).find()
        case _ => false
      }

    condition.conditionType match {
      case IsDefined => Some(row => values(row, name).nonEmpty)
      case IsUndefined => Some(row => values(row, name).isEmpty)

      case Equals => Some(text.map(toJson).fold(
        (row: JsObject) => values(row, name).isEmpty)(
        json => row => values(row, name).contains(json)
      ))

      case NotEquals => Some(text.map(toJson).fold(
        (row: JsObject) => values(row, name).nonEmpty)(
        json => row => !values(row, name).contains(json)
      ))

      case In =>
        val set = jsons
        Some(row => values(row, name).exists(set.contains))

      case NotIn =>
        val set = jsons
        Some(row => !values(row, name).exists(set.contains))

      case Greater => comparing(_ > 0)
      case GreaterEqual => comparing(_ >= 0)
      case Less => comparing(_ < 0)
      case LessEqual => comparing(_ <= 0)

      case RegexEquals => regex.map(finding)
      case RegexNotEquals => regex.map(pattern => (row: JsObject) => !finding(pattern)(row))

      case ContainsWords =>
        text.map { text =>
          val words = text.toLowerCase.split("\\s+").toSeq
          (row: JsObject) => values(row, name).exists {
            case JsString(string) => words.forall(string.toLowerCase.contains)
            case _ => false
          }
        }
    }
  }

  private def comparable(value1: JsValue, value2: JsValue) =
    (value1, value2) match {
      case (JsNumber(_), JsNumber(_)) | (JsString(_), JsString(_)) => true
      case _ => false
    }
}
