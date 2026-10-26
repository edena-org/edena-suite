package org.edena.ada.web.models

import org.edena.ada.server.field.{FieldType, FieldTypeFactory}
import org.edena.ada.server.models.{Category, DataSetSetting, Field, FilterShowFieldStyle, RecordViewSetting, URLType}
import org.edena.core.field.FieldTypeId
import org.edena.store.json.JsObjectIdentity
import play.api.libs.json._
import reactivemongo.api.bson.BSONObjectID

import scala.annotation.tailrec
import scala.math.Ordering.Implicits.seqOrdering
import scala.util.Try
import scala.util.matching.Regex
import org.edena.core.DefaultTypes.Seq

/**
  * The defined values of a record (a data set item) shown on its page, grouped by the fields' categories.
  *
  * @param label the category's path, e.g. "Examination › Tests"; none for the fields with no category, if no field has one
  * @param key identifies the group of a data set (the category's id), e.g. to remember that a user collapsed it
  * @param collapsed shown collapsed at first
  */
case class RecordFieldGroup(
  label: Option[String],
  fields: Seq[RecordField],
  key: String = RecordView.OtherFieldsKey,
  collapsed: Boolean = false
)

/**
  * A data set's category as a section of the record page, for the setting's editor.
  *
  * @param names the names of the categories from the root one, this one last
  * @param labels their labels
  */
case class RecordSection(
  names: Seq[String],
  labels: Seq[String]
) {
  def name: String = names.last
  def label: String = labels.mkString(RecordView.PathDelimiter)
}

/**
  * What the setting's editor of a data set's record page needs.
  *
  * @param fields the names and labels of the data set's fields
  * @param sections the categories in the tree's order
  * @param defaultTitle the item name
  * @param defaultView the name and the table columns of the default view
  */
case class RecordViewEditorData(
  fields: Seq[(String, Option[String])],
  sections: Seq[RecordSection],
  defaultTitle: String,
  defaultView: Option[(String, Seq[String])]
)

/**
  * @param showName the name is shown with the label (by the data set's field style)
  * @param fieldType none for a value with no field in the dictionary
  */
case class RecordField(
  name: String,
  label: String,
  showName: Boolean,
  fieldType: Option[FieldTypeId.Value],
  isArray: Boolean,
  value: RecordValue
)

/**
  * A value displayed according to its field's type.
  */
sealed trait RecordValue

object RecordValue {
  case class NumberValue(text: String) extends RecordValue
  case class DateValue(text: String) extends RecordValue
  case class BooleanValue(value: Boolean, text: String) extends RecordValue
  // an enum value's label, and the stored value
  case class EnumValue(label: String, value: String) extends RecordValue
  // a String or Text value; a long one is shown cut at first
  case class TextValue(text: String, long: Boolean) extends RecordValue
  case class LinkValue(url: String) extends RecordValue
  // pretty-printed; a long one is shown cut at first
  case class JsonValue(text: String, long: Boolean) extends RecordValue
  // the first elements of an array (at most `RecordView.ArrayMaxShownSize`) and its size; a list of lines if they're long
  case class ArrayValue(elements: Seq[RecordValue], size: Int, asList: Boolean) extends RecordValue
  // an array's null element
  case object NullValue extends RecordValue
  // no value (of a summary field)
  case object EmptyValue extends RecordValue
  // a value not of its field's type, as stored
  case class InvalidValue(json: String, message: String) extends RecordValue
}

object RecordView {

  import RecordValue._

  // a text with more characters or lines is shown cut at first, with "Show more"
  val TextPreviewLength = 300
  val TextPreviewLines = 5

  // a pretty-printed JSON with more lines is shown cut at first
  val JsonPreviewLines = 12

  // the elements of an array shown at first, and at most
  val ArrayPreviewSize = 20
  val ArrayMaxShownSize = 1000

  // array elements with a longer (or a multi-line) text are shown as a list of lines, not side by side
  val ArrayInlineMaxLength = 60

  val OtherFieldsLabel = "Other fields"
  val OtherFieldsKey = "other"

  val PathDelimiter = " › "

  // a field's value in a title, e.g. "Patient {{PATIENT_ID}}"
  private val titleFieldPattern = """\{\{\s*([^{}]+?)\s*\}\}""".r

  /**
    * The name of a data set's item, e.g. "Patient", by default "<data set name> Item".
    */
  def itemName(
    dataSetName: String,
    setting: DataSetSetting
  ): String =
    setting.displayItemName.map(_.trim).filter(_.nonEmpty).getOrElse(dataSetName + " Item")

  /**
    * The groups of a record's defined values: one per category with fields and the values of the fields with no
    * category last. The categories are in the tree's order (siblings by label), the setting's section order first; the
    * fields are ordered by label. The id and the setting's hidden fields are left out.
    */
  def fieldGroups(
    item: JsObject,
    fields: Traversable[Field],
    categories: Traversable[Category],
    ftf: FieldTypeFactory,
    showFieldStyle: Option[FilterShowFieldStyle.Value] = None,
    setting: RecordViewSetting = RecordViewSetting()
  ): Seq[RecordFieldGroup] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    val idCategoryMap = categories.flatMap(category => category._id.map((_, category))).toMap
    val hiddenFieldNames = setting.hiddenFieldNames.toSet

    val categoryIdFields = item.fields.toSeq.collect {
      case (name, json) if name != JsObjectIdentity.name && json != JsNull && !hiddenFieldNames.contains(name) =>
        val field = nameFieldMap.get(name)
        val categoryId = field.flatMap(_.categoryId).filter(idCategoryMap.contains)

        (categoryId, recordField(name, json, field, ftf, showFieldStyle))
    }

    def sorted(fields: Seq[RecordField]) =
      fields.sortBy(field => (field.label.toLowerCase, field.name))

    val rankMap = sectionRankMap(setting.sectionOrder)
    val collapsedNames = setting.collapsedSections.toSet

    val categoryGroups = categoryIdFields.collect { case (Some(categoryId), field) => (categoryId, field) }
      .groupBy(_._1).toSeq
      .map { case (categoryId, idFields) => (categoryPath(categoryId, idCategoryMap), categoryId, idFields.map(_._2)) }
      .sortBy { case (path, categoryId, _) =>
        (sectionRank(path.map(_.name), rankMap), path.map(_.labelOrElseName.toLowerCase), categoryId.stringify)
      }
      .map { case (path, categoryId, fields) =>
        RecordFieldGroup(
          Some(path.map(_.labelOrElseName).mkString(PathDelimiter)),
          sorted(fields),
          categoryId.stringify,
          path.lastOption.exists(category => collapsedNames.contains(category.name))
        )
      }

    val otherFields = categoryIdFields.collect { case (None, field) => field }

    val otherGroups =
      if (otherFields.isEmpty)
        Nil
      else
        Seq(RecordFieldGroup(if (categoryGroups.nonEmpty) Some(OtherFieldsLabel) else None, sorted(otherFields)))

    categoryGroups ++ otherGroups
  }

  /**
    * The summary at the top of a record's page: the fields' values (shown empty if the record has none), the hidden
    * fields and the id left out.
    */
  def summaryFields(
    item: JsObject,
    fieldNames: Seq[String],
    fields: Traversable[Field],
    ftf: FieldTypeFactory,
    showFieldStyle: Option[FilterShowFieldStyle.Value] = None,
    setting: RecordViewSetting = RecordViewSetting()
  ): Seq[RecordField] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    val hiddenFieldNames = setting.hiddenFieldNames.toSet

    fieldNames.distinct.filterNot(name => name == JsObjectIdentity.name || hiddenFieldNames.contains(name)).flatMap { name =>
      val json = (item \ name).toOption.filter(_ != JsNull)
      val field = nameFieldMap.get(name)

      json match {
        case Some(json) => Some(recordField(name, json, field, ftf, showFieldStyle))

        // a field with no value of the record, unless it's not in the dictionary (any more)
        case None => field.map(field =>
          recordField(name, JsNull, Some(field), ftf, showFieldStyle).copy(value = EmptyValue)
        )
      }
    }
  }

  /**
    * A record's title: `{{FIELD_NAME}}` in the template replaced by the record's (display) value of the field, none if
    * it's blank.
    */
  def title(
    template: String,
    item: JsObject,
    fields: Traversable[Field],
    ftf: FieldTypeFactory
  ): Option[String] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap

    def displayText(name: String) =
      (item \ name).toOption.filter(_ != JsNull).map { json =>
        nameFieldMap.get(name).flatMap { field =>
          Try(ftf(field.fieldTypeSpec).jsonToDisplayString(json)).toOption
        }.getOrElse(
          json match {
            case JsString(text) => text
            case _ => Json.stringify(json)
          }
        )
      }.getOrElse("")

    val text = titleFieldPattern.replaceAllIn(template, matched => Regex.quoteReplacement(displayText(matched.group(1))))
    Some(text.replaceAll("\\s+", " ").trim).filter(_.nonEmpty)
  }

  /**
    * A data set's categories as the record page's sections, in the category tree's order (siblings by label).
    */
  def sections(
    categories: Traversable[Category]
  ): Seq[RecordSection] = {
    val idCategoryMap = categories.flatMap(category => category._id.map((_, category))).toMap

    categories.toSeq.flatMap(_._id).map(categoryPath(_, idCategoryMap)).filter(_.nonEmpty)
      .sortBy(path => path.map(_.labelOrElseName.toLowerCase))
      .map(path => RecordSection(path.map(_.name), path.map(_.labelOrElseName)))
  }

  /**
    * The sections in the record page's order: those in the section order first, the others after in the given order.
    */
  def orderedSections(
    sections: Seq[RecordSection],
    sectionOrder: Seq[String]
  ): Seq[RecordSection] = {
    val rankMap = sectionRankMap(sectionOrder)

    sections.zipWithIndex
      .sortBy { case (section, index) => (sectionRank(section.names, rankMap), index) }
      .map(_._1)
  }

  private def sectionRankMap(sectionOrder: Seq[String]) =
    sectionOrder.zipWithIndex.reverse.toMap

  // the rank of the closest category listed in a path (the category itself first); one not listed comes last
  private def sectionRank(pathNames: Seq[String], rankMap: Map[String, Int]) =
    pathNames.reverseIterator.flatMap(rankMap.get).nextOption().getOrElse(Int.MaxValue)

  // the categories from the root one (a parent cycle ends the path)
  private def categoryPath(
    categoryId: BSONObjectID,
    idCategoryMap: Map[BSONObjectID, Category]
  ): Seq[Category] = {
    @tailrec
    def pathAux(categoryId: BSONObjectID, path: Seq[Category], visitedIds: Set[BSONObjectID]): Seq[Category] =
      idCategoryMap.get(categoryId) match {
        case Some(category) if !visitedIds.contains(categoryId) =>
          category.parentId match {
            case Some(parentId) => pathAux(parentId, category +: path, visitedIds + categoryId)
            case None => category +: path
          }

        case _ => path
      }

    pathAux(categoryId, Nil, Set())
  }

  private def recordField(
    name: String,
    json: JsValue,
    field: Option[Field],
    ftf: FieldTypeFactory,
    showFieldStyle: Option[FilterShowFieldStyle.Value]
  ) = {
    val label = field.flatMap(_.label).map(_.trim).filter(_.nonEmpty)

    val (shownLabel, showName) =
      showFieldStyle.getOrElse(FilterShowFieldStyle.LabelsAndNamesOnlyIfLabelUndefined) match {
        case FilterShowFieldStyle.NamesOnly => (name, false)
        case FilterShowFieldStyle.NamesAndLabels => (label.getOrElse(name), label.exists(_ != name))
        case _ => (label.getOrElse(name), false)
      }

    RecordField(
      name,
      shownLabel,
      showName,
      field.map(_.fieldType),
      field.map(_.isArray).getOrElse(json.isInstanceOf[JsArray]),
      field.map(fieldValue(json, _, ftf)).getOrElse(untypedValue(json))
    )
  }

  /**
    * A (non-null) value of a field, displayed according to its type; a value not of the type is shown as stored.
    */
  def fieldValue(
    json: JsValue,
    field: Field,
    ftf: FieldTypeFactory
  ): RecordValue =
    if (field.fieldType == FieldTypeId.Json)
      jsonValue(json)
    else if (field.isArray) {
      val elementType = ftf(field.fieldTypeSpec.copy(isArray = false))

      // a single value of an array field is shown as its element
      val elements = json match {
        case JsArray(elements) => elements.toSeq
        case _ => Seq(json)
      }

      arrayValue(
        elements.take(ArrayMaxShownSize).map(scalarValue(_, field, elementType)),
        elements.size,
        field.fieldType == FieldTypeId.Text
      )
    } else
      scalarValue(json, field, ftf(field.fieldTypeSpec))

  private def scalarValue(
    json: JsValue,
    field: Field,
    fieldType: FieldType[_]
  ): RecordValue =
    if (json == JsNull)
      NullValue
    else
      Try {
        def displayString = fieldType.jsonToDisplayString(json)

        field.fieldType match {
          case FieldTypeId.Integer | FieldTypeId.Double =>
            NumberValue(displayString)

          // the default display format with a time of midnight shown as a date
          case FieldTypeId.Date =>
            DateValue(displayString.stripSuffix(" 00:00:00"))

          case FieldTypeId.Boolean =>
            BooleanValue(fieldType.asValueOf[Boolean].jsonToValue(json).get, displayString)

          case FieldTypeId.Enum =>
            EnumValue(displayString, Json.stringify(json))

          case FieldTypeId.String | FieldTypeId.Text if field.displayAsURLType.contains(URLType.GET) && isLink(displayString) =>
            LinkValue(displayString.trim)

          case _ =>
            textValue(displayString)
        }
      }.getOrElse(
        InvalidValue(Json.stringify(json), s"Not a valid ${field.fieldType} value.")
      )

  // a value with no field in the dictionary, by its JSON type
  private def untypedValue(json: JsValue): RecordValue =
    json match {
      case JsNull => NullValue
      case JsString(text) => textValue(text)
      case JsNumber(_) => NumberValue(Json.stringify(json))
      case JsBoolean(value) => BooleanValue(value, value.toString)
      case JsArray(elements) if !elements.exists(_.isInstanceOf[JsObject]) && !elements.exists(_.isInstanceOf[JsArray]) =>
        arrayValue(elements.toSeq.take(ArrayMaxShownSize).map(untypedValue), elements.size, false)
      case _ => jsonValue(json)
    }

  private def textValue(text: String) =
    TextValue(text, text.length > TextPreviewLength || text.count(_ == '\n') >= TextPreviewLines)

  private def jsonValue(json: JsValue) = {
    val text = Json.prettyPrint(json)
    JsonValue(text, text.count(_ == '\n') >= JsonPreviewLines)
  }

  private def arrayValue(
    elements: Seq[RecordValue],
    size: Int,
    asList: Boolean
  ) = {
    def isLong(text: String) = text.length > ArrayInlineMaxLength || text.contains('\n')

    val hasLongElement = elements.exists {
      case TextValue(text, _) => isLong(text)
      case InvalidValue(json, _) => isLong(json)
      case _ => false
    }

    ArrayValue(elements, size, asList || hasLongElement)
  }

  // only a web URL or a path is a link (never e.g. "javascript:")
  private def isLink(url: String) = {
    val lowerCaseUrl = url.trim.toLowerCase
    lowerCaseUrl.startsWith("https://") || lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("/")
  }

  /**
    * The icon (of Bootstrap Icons) of a field's type.
    */
  def typeIcon(field: RecordField): String =
    field.fieldType match {
      case Some(FieldTypeId.Integer) | Some(FieldTypeId.Double) => "bi-123"
      case Some(FieldTypeId.Date) => "bi-calendar3"
      case Some(FieldTypeId.Boolean) => "bi-toggle-on"
      case Some(FieldTypeId.Enum) => "bi-tag"
      case Some(FieldTypeId.String) => "bi-fonts"
      case Some(FieldTypeId.Text) => "bi-text-paragraph"
      case Some(FieldTypeId.Json) => "bi-braces"
      case _ => "bi-question-circle"
    }

  def typeName(field: RecordField): String =
    field.fieldType.map { fieldType =>
      if (field.isArray) s"$fieldType array" else fieldType.toString
    }.getOrElse(
      "Not in the dictionary"
    )
}
