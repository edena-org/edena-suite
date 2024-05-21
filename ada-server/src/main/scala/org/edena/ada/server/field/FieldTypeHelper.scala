package org.edena.ada.server.field

import org.edena.ada.server.field.inference.FieldTypeInferrerFactory

object FieldTypeHelper {

  val nullAliases = Set("", "na", "n/a", "null")
  val dateFormats = Seq(
    "yyyy-MM-dd HH:mm:ss.SS",
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd HH",
    "yyyy-MM-dd",
    "dd-MMM-yyyy HH:mm:ss",
    "dd-MMM-yyyy HH:mm",
    "dd-MMM-yyyy HH",
    "dd-MMM-yyyy",
    "dd.MMM.yyyy HH:mm:ss",
    "dd.MMM.yyyy HH:mm",
    "dd.MMM.yyyy HH",
    "dd.MMM.yyyy",
    "dd.MM.yyyy HH:mm:ss",
    "dd.MM.yyyy HH:mm",
    "dd.MM.yyyy HH",
    "dd.MM.yyyy",
    "MM.yyyy",
    "MM/dd/yyyy"
  )
  val displayDateFormat = "yyyy-MM-dd HH:mm:ss"
  val maxEnumValuesCount = 20
  val minAvgValuesPerEnum = 1.5

  val arrayDelimiter = ","

  def nullAliasesOrDefault(explicitAliases: Traversable[String]) =
    if (explicitAliases.nonEmpty)
      explicitAliases.map(_.toLowerCase).toSet
    else
      FieldTypeHelper.nullAliases

  def fieldTypeFactory(
    nullAliases: Set[String] = nullAliases,
    dateFormats: Traversable[String] = dateFormats,
    displayDateFormat: String = displayDateFormat,
    arrayDelimiter: String = arrayDelimiter,
    booleanIncludeNumbers: Boolean = true
  ) = FieldTypeFactory(nullAliases, dateFormats, displayDateFormat, arrayDelimiter, booleanIncludeNumbers)

  def fieldTypeInferrerFactory(
    nullAliases: Set[String] = nullAliases,
    dateFormats: Traversable[String] = dateFormats,
    displayDateFormat: String = displayDateFormat,
    booleanIncludeNumbers: Boolean = true,
    maxEnumValuesCount: Int = maxEnumValuesCount,
    minAvgValuesPerEnum: Double = minAvgValuesPerEnum,
    arrayDelimiter: String = arrayDelimiter
  ) = {
    val ftf = fieldTypeFactory(
      nullAliases,
      dateFormats,
      displayDateFormat,
      arrayDelimiter,
      booleanIncludeNumbers
    )

    new FieldTypeInferrerFactory(
      ftf,
      maxEnumValuesCount,
      minAvgValuesPerEnum,
      arrayDelimiter
    )
  }

  val fieldTypeInferrer = fieldTypeInferrerFactory().ofString
  val jsonFieldTypeInferrer = fieldTypeInferrerFactory().ofJson
}