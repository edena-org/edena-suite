package org.edena.core

import org.edena.core.store.Criterion
import org.edena.core.store._

/**
  * Filter condition to be transformed to a repo criterion.
  *
  * @author Peter Banda
  */
// TODO: move to Play
case class FilterCondition(
  fieldName: String,
  fieldLabel: Option[String],
  conditionType: ConditionType.Value,
  value: Option[String],
  valueLabel: Option[String]
) {
  def fieldLabelOrElseName = fieldLabel.getOrElse(fieldName)
}

object ConditionType extends Enumeration {
  val Equals = Value("=")
  val NotEquals = Value("!=")
  val RegexEquals = Value("like")
  val RegexNotEquals = Value("not like")
  val In = Value("in")
  val NotIn = Value("nin")
  val Greater = Value(">")
  val GreaterEqual = Value(">=")
  val Less = Value("<")
  val LessEqual = Value("<=")
}

object FilterCondition {
  import ConditionType._

  def toCriteria(
    valueConverters: Map[String, String => Option[Any]],
    filterConditions: Seq[FilterCondition]
  ): Criterion = {
    // implicitly use AND to combine criteria/conditions
    val criteria = filterConditions.flatMap(condition => toCriterion(valueConverters)(condition))
    And(criteria)
  }

  def toCriterion(
    valueConverters: Map[String, String => Option[Any]])(
    filterCondition: FilterCondition
  ): Option[Criterion] = {
    val fieldName = filterCondition.fieldName

    // convert values if any converters provided
    def convertValue(text: Option[String]): Option[Any] = text.flatMap( text =>
      valueConverters.get(fieldName).map(converter =>
        converter.apply(text.trim)
      ).getOrElse(Some(text.trim)) // if no converter found use a provided string value
    )

    val value =  filterCondition.value

    def convertedValue = convertValue(value)
    def convertedValues: Seq[Any] = {
      value.map(_.split(",").toSeq.flatMap(x => convertValue(Some(x)))).getOrElse(Nil)
    }

    filterCondition.conditionType match {
      case Equals => Some(
        convertedValue.map(
          EqualsCriterion(fieldName, _)
        ).getOrElse(
          EqualsNullCriterion(fieldName)
        )
      )

      case RegexEquals => Some(RegexEqualsCriterion(fieldName, value.getOrElse("")))            // string expected

      case RegexNotEquals => Some(RegexNotEqualsCriterion(fieldName, value.getOrElse("")))            // string expected

      case NotEquals => Some(
        convertedValue.map(
          NotEqualsCriterion(fieldName, _)
        ).getOrElse(
          NotEqualsNullCriterion(fieldName)
        )
      )

      case In => Some(InCriterion(fieldName, convertedValues))

      case NotIn => Some(NotInCriterion(fieldName, convertedValues))

      case Greater => convertedValue.map(GreaterCriterion(fieldName, _))

      case GreaterEqual => convertedValue.map(GreaterEqualCriterion(fieldName, _))

      case Less => convertedValue.map(LessCriterion(fieldName, _))

      case LessEqual => convertedValue.map(LessEqualCriterion(fieldName, _))
    }
  }
}