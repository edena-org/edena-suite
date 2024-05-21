package org.edena.ada.web.services.widgetgen

import org.edena.ada.web.models.{CategoricalCountWidget, Count, NumericalCountWidget, Widget}
import org.edena.ada.server.field.{FieldType, FieldTypeHelper, FieldUtil}
import FieldUtil.FieldOps
import org.edena.ada.server.AdaException
import org.edena.ada.server.models._
import org.edena.ada.web.util.{fieldLabel, shorten}
import org.edena.core.calc.impl.{CumulativeOrderedCountsCalcTypePack, GroupCumulativeOrderedCountsCalcTypePack, NumericDistributionCountsCalcTypePack}
import org.edena.core.field.FieldTypeId

/**
  * Generator of a cumulative count widget for numeric field types.
  */
object NumericCumulativeCountWidgetGenerator extends CumulativeCountWidgetGenerator[NumericalCountWidget[Any]] {

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (valueCounts: CumulativeOrderedCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      if (field.isNumeric) {
        val counts = valueCounts.map { case (value, count) => Count(value, count) }

        // create a widget
        createNumericWidget(spec, field, None)(Seq(("All", counts)))
      } else {
        throw new AdaException(s"Field '${field.name}' of type ${field.fieldType} is not numeric but called for a numeric cumulative count widget generation.")
      }
    }
}

/**
  * Generator of a cumulative count widget for categorical field types.
  */
object CategoricalCumulativeCountWidgetGenerator extends CumulativeCountWidgetGenerator[CategoricalCountWidget] {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (valueCounts: CumulativeOrderedCountsCalcTypePack[Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
      val counts = createStringCountsDefined(valueCounts, fieldType)

      // create a widget
      createCategoricalWidget(spec, field, None)(Seq(("All", counts)))
    }
}

/**
  * Abstract generator of a cumulative count widget (both numeric and categorical)
  *
  * @tparam W Result widget type
  */
abstract class CumulativeCountWidgetGenerator[W <: Widget] extends CalculatorWidgetGenerator[CumulativeCountWidgetSpec, W, CumulativeOrderedCountsCalcTypePack[Any]]
  with CumulativeCountWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[CumulativeCountWidgetSpec] {

  override protected val seqExecutor = cumulativeOrderedCountsAnySeqExec

  override protected val supportArray = true

  override protected def extraStreamCriterion(
    spec: CumulativeCountWidgetSpec,
    fields: Seq[Field]
  ) = Some(withNotNull(fields))
}


/**
  * Generator of a grouped cumulative count widget for numeric field types.
  */
object NumericGroupCumulativeCountWidgetGenerator extends GroupCumulativeCountWidgetGenerator[NumericalCountWidget[Any]] {

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (groupCounts:  GroupCumulativeOrderedCountsCalcTypePack[Any, Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get

      if (field.isNumeric) {
        val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
        val stringGroupCounts = toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, valueCounts) =>
          val counts = valueCounts.map { case (value, count) => Count(value, count)}
          (groupString, counts)
        }

        // create a widget
        createNumericWidget(spec, field, Some(groupField))(stringGroupCounts)
      } else {
        throw new AdaException(s"Field '${field.name}' of type ${field.fieldType} is not numeric but called for a numeric cumulative count widget generation.")
      }
    }
}

/**
  * Generator of a grouped cumulative count widget for categorical field types.
  */
object CategoricalGroupCumulativeCountWidgetGenerator extends GroupCumulativeCountWidgetGenerator[CategoricalCountWidget] {

  override def apply(
    spec: CumulativeCountWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (groupCounts:  GroupCumulativeOrderedCountsCalcTypePack[Any, Any]#OUT) => {
      val field = fieldNameMap.get(spec.fieldName).get
      val groupField = fieldNameMap.get(spec.groupFieldName.get).get
      val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
      val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

      val stringGroupCounts = toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, valueCounts) =>
        (groupString, createStringCountsDefined(valueCounts, fieldType))
      }

      // create a widget
      createCategoricalWidget(spec, field, Some(groupField))(stringGroupCounts)
    }
}

/**
  * Abstract generator of a grouped cumulative count widget (both numeric and categorical)
  *
  * @tparam W Result widget type
  */
abstract class GroupCumulativeCountWidgetGenerator[W <: Widget] extends CalculatorWidgetGenerator[CumulativeCountWidgetSpec, W, GroupCumulativeOrderedCountsCalcTypePack[Any, Any]]
  with CumulativeCountWidgetGeneratorHelper
  with NoOptionsCalculatorWidgetGenerator[CumulativeCountWidgetSpec] {

  protected val ftf = FieldTypeHelper.fieldTypeFactory()

  override protected val seqExecutor = groupCumulativeOrderedCountsAnySeqExec[Any]

  override protected val supportArray = true

  override protected def extraStreamCriterion(
    spec: CumulativeCountWidgetSpec,
    fields: Seq[Field]
  ) = Some(withNotNull(fields.tail))
}


/**
  * Helper trait for generation of cumulative count widget(s).
  */
trait CumulativeCountWidgetGeneratorHelper {

  protected val defaultNumericBinCount = 20

  protected def createNumericWidget(
    spec: CumulativeCountWidgetSpec,
    field: Field,
    groupField: Option[Field]
  ) =
    (countSeries:  Traversable[(String, Traversable[Count[Any]])]) => {
      val displayOptions = spec.displayOptions
      val title = displayOptions.title.getOrElse(createTitle(field, groupField))

      val nonZeroCountSeries = countSeries.filter(_._2.exists(_.count > 0))
      if (nonZeroCountSeries.nonEmpty) {
        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
        val widget = NumericalCountWidget(
          title,
          field.name,
          field.labelOrElseName,
          spec.relativeValues,
          true,
          field.fieldType,
          nonZeroCountSeries.toSeq,
          initializedDisplayOptions
        )

        Some(widget)
      } else
        None
    }

  protected def createCategoricalWidget(
    spec: CumulativeCountWidgetSpec,
    field: Field,
    groupField: Option[Field]
  ) =
    (countSeries:  Seq[(String, Traversable[Count[String]])]) => {
      val displayOptions = spec.displayOptions
      val title = displayOptions.title.getOrElse(createTitle(field, groupField))

      val nonZeroCountSeries = countSeries.filter(_._2.exists(_.count > 0))
      if (nonZeroCountSeries.nonEmpty) {
        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Column)))

        // create a categorical widget
        val widget = CategoricalCountWidget(
          title,
          field.name,
          field.labelOrElseName,
          false,
          true,
          spec.relativeValues,
          true,
          nonZeroCountSeries,
          initializedDisplayOptions
        )
        Some(widget)
      } else
        None
    }

  protected def toGroupStringValues[G, T](
    groupCounts: Traversable[(Option[G], Traversable[T])],
    groupFieldType: FieldType[G]
  ): Seq[(String, Traversable[T])] =
    groupCounts.toSeq.sortBy(_._1.isEmpty).map { case (group, values) =>
      val groupString = group match {
        case Some(group) => groupFieldType.valueToDisplayString(Some(group))
        case None => "Undefined"
      }
      (groupString, values)
    }

  protected def createStringCounts[T](
    counts: Traversable[(Option[T], Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.map(_.toString)
      val label = value.map(value => fieldType.valueToDisplayString(Some(value))).getOrElse("Undefined")
      Count(label, count, stringKey)
    }

  protected def createStringCountsDefined[T](
    counts: Traversable[(T, Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.toString
      val label = fieldType.valueToDisplayString(Some(value))
      Count(label, count, Some(stringKey))
    }

  protected def createNumericCounts(
    counts: NumericDistributionCountsCalcTypePack#OUT,
    convert: Option[BigDecimal => Any] = None
  ): Seq[Count[_]] =
    counts.toSeq.sortBy(_._1).map { case (xValue, count) =>
      val convertedValue = convert.map(_.apply(xValue)).getOrElse(xValue.toDouble)
      Count(convertedValue, count, None)
    }

  protected def convertNumeric(fieldType: FieldTypeId.Value) =
    fieldType match {
      case FieldTypeId.Date =>
        val convert = {ms: BigDecimal => new java.util.Date(ms.setScale(0, BigDecimal.RoundingMode.CEILING).toLongExact)}
        Some(convert)
      case _ => None
    }

  // function that converts dist counts to cumulative counts by applying simple running sum
  protected def toCumCounts[T](
    distCountsSeries: Traversable[(String, Traversable[Count[T]])]
  ): Traversable[(String, Seq[Count[T]])] =
    distCountsSeries.map { case (seriesName, distCounts) =>
      val distCountsSeq = distCounts.toSeq
      val cumCounts = distCountsSeq.scanLeft(0) { case (sum, count) =>
        sum + count.count
      }
      val labeledDistCounts: Seq[Count[T]] = distCountsSeq.map(_.value).zip(cumCounts.tail).map { case (value, count) =>
        Count(value, count)
      }
      (seriesName, labeledDistCounts)
    }

  protected def createTitle(
    field: Field,
    groupField: Option[Field]
  ): String =
    groupField match {
      case Some(groupField) =>
        shorten(fieldLabel(field), 25) + " by " + shorten(fieldLabel(groupField), 25)

      case None =>
        fieldLabel(field)
    }
}