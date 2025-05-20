package org.edena.ada.web.services.widgetgen

import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models._
import org.edena.ada.web.models.{CategoricalCheckboxCountWidget, Count}
import org.edena.core.calc.impl.UniqueDistributionCountsCalc.UniqueDistributionCountsCalcTypePack
import org.edena.core.store._
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import spire.ClassTag

import org.edena.core.DefaultTypes.Seq

private class CategoricalCheckboxCountWidgetGenerator(criterion: Criterion) extends CalculatorWidgetGenerator[CategoricalCheckboxWidgetSpec, CategoricalCheckboxCountWidget, UniqueDistributionCountsCalcTypePack[Any]]
  with DistributionWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[CategoricalCheckboxWidgetSpec] {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val booleanType = ftf(FieldTypeSpec(FieldTypeId.Boolean)).asValueOf[Boolean]

  override protected val seqExecutor = uniqueDistributionCountsSeqExec[Any]

  override protected val supportArray = true

  override def apply(
    spec: CategoricalCheckboxWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (uniqueCounts: UniqueDistributionCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

      // TODO: this should be passed to the function to handle String, and Int fields correctly
      // only enum and boolean types have enclosed all count defs
      val allZeroCounts =
        field.fieldType match {
          case FieldTypeId.Enum =>
            field.enumValues.map { case (key, label) => Count(label, 0, Some(key)) }

          case FieldTypeId.Boolean =>
            def boolZeroCount(value: Boolean) = Count(booleanType.valueToDisplayString(Some(value)), 0, Some(value.toString))
            Seq(boolZeroCount(true), boolZeroCount(false))

          case _ => Nil
        }

      val nonZeroCounts = createStringCounts(uniqueCounts, fieldType)
      val nonZeroCountValues = nonZeroCounts.map(_.value).toSet
      val zeroCounts = allZeroCounts.filterNot(count => nonZeroCountValues.contains(count.value))

      val allCounts = nonZeroCounts ++ zeroCounts

      // nested search for value criteria matching a given widget field name
      def fieldValueCriteriaAux(criterion: Criterion): Seq[Criterion] =
        criterion match {
          case c: And => c.criteria.flatMap(fieldValueCriteriaAux)
          case c: Or => c.criteria.flatMap(fieldValueCriteriaAux)
          case NoCriterion => Nil
          case c: ValueCriterion[_] => if (c.fieldName == spec.fieldName) Seq(c) else Nil
        }

      val fieldValueCriteria = fieldValueCriteriaAux(criterion)

      def findCheckedValues[E: ClassTag](fun: E => Seq[String]) =
        fieldValueCriteria.collect { case x: E => x }.headOption.map(fun).getOrElse(Nil).toSet

      val inValues = findCheckedValues[InCriterion[Any]] {
        _.value.map { value =>
          fieldType.valueToDisplayString(Some(value))
        }
      }

      val equalsValues = findCheckedValues[EqualsCriterion[Any]] { criterion =>
        Seq(fieldType.valueToDisplayString(Some(criterion.value)))
      }

      val notInValues = findCheckedValues[NotInCriterion[Any]] {
        _.value.map { value =>
          fieldType.valueToDisplayString(Some(value))
        }
      }

      val notEqualsValues = findCheckedValues[NotEqualsCriterion[Any]] { criterion =>
        Seq(fieldType.valueToDisplayString(Some(criterion.value)))
      }

      val allValues = allZeroCounts.map(_.value).toSet

      val checkedCounts = if (allValues.nonEmpty) {
        val checkedValues = intersect(inValues, equalsValues)
        val checkedValues2 = allValues.diff(notInValues.union(notEqualsValues))
        val finalCheckedValues = intersect(checkedValues, checkedValues2)

        allCounts.map { count =>
          val checked = finalCheckedValues.contains(count.value)
          (checked, count)
        }
      } else {
        // check everything
        allCounts.map { count =>
          (true, count)
        }
      }

      Some(CategoricalCheckboxCountWidget(field.labelOrElseName, spec.fieldName, checkedCounts.toSeq.sortBy(_._2.value), spec.displayOptions))
    }

  private def intersect(
    set1: Set[String],
    set2: Set[String]
  ) =
    if (set1.nonEmpty && set2.nonEmpty)
      set1.intersect(set2)
    else if (set1.nonEmpty)
      set1
    else
      set2
}

object CategoricalCheckboxCountWidgetGenerator {
  type Gen = CalculatorWidgetGenerator[CategoricalCheckboxWidgetSpec, CategoricalCheckboxCountWidget, UniqueDistributionCountsCalcTypePack[Any]]

  def apply(criterion: Criterion): Gen = new CategoricalCheckboxCountWidgetGenerator(criterion)
}
