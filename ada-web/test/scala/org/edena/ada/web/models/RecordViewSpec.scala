package org.edena.ada.web.models

import java.text.SimpleDateFormat

import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models.{Category, DataSetSetting, Field, FilterShowFieldStyle, RecordViewSetting, URLType}
import org.edena.ada.web.models.RecordValue._
import org.edena.core.field.FieldTypeId
import org.edena.store.json.BSONObjectIDFormat
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsArray, JsNull, JsObject, JsValue, Json}
import reactivemongo.api.bson.BSONObjectID

class RecordViewSpec extends AnyFlatSpec with Matchers {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private def value(field: Field, json: JsValue) = RecordView.fieldValue(json, field, ftf)

  private def dateMs(text: String) = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(text).getTime

  private val genderField = Field("GENDER", Some("Gender"), FieldTypeId.Enum, enumValues = Map("0" -> "Male", "1" -> "Female"))

  "A record value" should "be displayed by its field's type" in {
    value(Field("AGE", fieldType = FieldTypeId.Integer), Json.toJson(42)) shouldBe NumberValue("42")
    value(Field("WEIGHT", fieldType = FieldTypeId.Double, displayDecimalPlaces = Some(1)), Json.toJson(71.26)) shouldBe NumberValue("71.3")
    value(genderField, Json.toJson(1)) shouldBe EnumValue("Female", "1")

    val consentField = Field("CONSENT", fieldType = FieldTypeId.Boolean, displayTrueValue = Some("Yes"))
    value(consentField, Json.toJson(true)) shouldBe BooleanValue(true, "Yes")
    value(consentField, Json.toJson(false)) shouldBe BooleanValue(false, "false")
  }

  it should "show a date's time unless it's midnight" in {
    val dateField = Field("VISIT_DATE", fieldType = FieldTypeId.Date)

    value(dateField, Json.toJson(dateMs("2024-03-01 13:45:10"))) shouldBe DateValue("2024-03-01 13:45:10")
    value(dateField, Json.toJson(dateMs("2024-03-01 00:00:00"))) shouldBe DateValue("2024-03-01")
  }

  it should "be a text, cut at first if it's long or has many lines" in {
    val noteField = Field("NOTE", fieldType = FieldTypeId.Text)

    value(noteField, Json.toJson("Follow-up")) shouldBe TextValue("Follow-up", false)
    value(noteField, Json.toJson("x" * RecordView.TextPreviewLength)) shouldBe TextValue("x" * RecordView.TextPreviewLength, false)
    value(noteField, Json.toJson("x" * (RecordView.TextPreviewLength + 1))).asInstanceOf[TextValue].long shouldBe true

    // more lines than shown at first
    val lines = (0 to RecordView.TextPreviewLines).map("line " + _)
    value(noteField, Json.toJson(lines.mkString("\n"))).asInstanceOf[TextValue].long shouldBe true
    value(noteField, Json.toJson(lines.init.mkString("\n"))).asInstanceOf[TextValue].long shouldBe false

    // a long String too
    value(Field("CODE"), Json.toJson("s" * 500)).asInstanceOf[TextValue].long shouldBe true
  }

  it should "be a link only for a GET URL field with a web URL or a path" in {
    val urlField = Field("REPORT", fieldType = FieldTypeId.String, displayAsURLType = Some(URLType.GET))

    value(urlField, Json.toJson("https://example.org/report?id=1")) shouldBe LinkValue("https://example.org/report?id=1")
    value(urlField, Json.toJson(" /dataSets/records?filter=1")) shouldBe LinkValue("/dataSets/records?filter=1")
    value(urlField, Json.toJson("javascript:alert(1)")) shouldBe TextValue("javascript:alert(1)", false)
    value(urlField, Json.toJson("JavaScript:alert(1)")) shouldBe TextValue("JavaScript:alert(1)", false)
    value(urlField.copy(displayAsURLType = Some(URLType.POST)), Json.toJson("https://example.org")) shouldBe TextValue("https://example.org", false)
  }

  it should "be a pretty-printed JSON for a Json field, cut at first if it has many lines" in {
    val dataField = Field("DATA", fieldType = FieldTypeId.Json)
    val json = Json.obj("a" -> 1, "b" -> Json.arr("x", "y"))

    value(dataField, json) shouldBe JsonValue(Json.prettyPrint(json), false)
    value(dataField.copy(isArray = true), Json.arr(json, JsNull)) shouldBe JsonValue(Json.prettyPrint(Json.arr(json, JsNull)), false)

    val bigJson = JsObject((1 to RecordView.JsonPreviewLines).map(i => ("key" + i, Json.toJson(i))))
    value(dataField, bigJson).asInstanceOf[JsonValue].long shouldBe true
  }

  it should "be shown as stored, with a message, if it's not of its field's type" in {
    value(Field("AGE", fieldType = FieldTypeId.Integer), Json.toJson("forty")) shouldBe InvalidValue("\"forty\"", "Not a valid Integer value.")
    value(genderField, Json.toJson(7)) shouldBe InvalidValue("7", "Not a valid Enum value.")
    value(Field("VISIT_DATE", fieldType = FieldTypeId.Date), Json.toJson("yesterday")) shouldBe InvalidValue("\"yesterday\"", "Not a valid Date value.")
  }

  "An array value" should "have its elements displayed by the field's type, side by side if they're short" in {
    value(genderField.copy(isArray = true), Json.arr(1, JsNull, 0)) shouldBe
      ArrayValue(Seq(EnumValue("Female", "1"), NullValue, EnumValue("Male", "0")), 3, false)

    value(Field("SCORES", fieldType = FieldTypeId.Double, isArray = true), Json.arr(1.5, "high")) shouldBe
      ArrayValue(Seq(NumberValue("1.5"), InvalidValue("\"high\"", "Not a valid Double value.")), 2, false)

    value(Field("CODES", isArray = true), Json.arr()) shouldBe ArrayValue(Nil, 0, false)
  }

  it should "be a list of lines for a Text field, or with a long element" in {
    value(Field("NOTES", fieldType = FieldTypeId.Text, isArray = true), Json.arr("a", "b")) shouldBe
      ArrayValue(Seq(TextValue("a", false), TextValue("b", false)), 2, true)

    val long = "c" * (RecordView.ArrayInlineMaxLength + 1)
    value(Field("CODES", isArray = true), Json.arr("a", long)).asInstanceOf[ArrayValue].asList shouldBe true
    value(Field("CODES", isArray = true), Json.arr("a", "b\nc")).asInstanceOf[ArrayValue].asList shouldBe true
    value(Field("CODES", isArray = true), Json.arr("a", "c" * RecordView.ArrayInlineMaxLength)).asInstanceOf[ArrayValue].asList shouldBe false
  }

  it should "have at most the max. shown elements, and its size" in {
    val ArrayValue(elements, size, _) = value(Field("SERIES", fieldType = FieldTypeId.Integer, isArray = true), JsArray((1 to 1500).map(Json.toJson(_))))

    size shouldBe 1500
    elements.size shouldBe RecordView.ArrayMaxShownSize
    elements.last shouldBe NumberValue(RecordView.ArrayMaxShownSize.toString)
  }

  it should "be the single element for a single value of an array field" in {
    value(genderField.copy(isArray = true), Json.toJson(0)) shouldBe ArrayValue(Seq(EnumValue("Male", "0")), 1, false)
  }

  // the fields and categories of a record's page

  private def category(name: String, parent: Option[Category] = None, label: Option[String] = None) =
    Category(Some(BSONObjectID.generate()), name, label, parentId = parent.flatMap(_._id))

  private val examination = category("examination", label = Some("Examination"))
  private val tests = category("tests", Some(examination), Some("Tests"))
  private val visit = category("visit", label = Some("Visit"))

  private val fields = Seq(
    Field("PATIENT_ID", Some("Patient"), FieldTypeId.String),
    Field("AGE", Some("Age"), FieldTypeId.Integer, categoryId = examination._id),
    Field("WEIGHT", Some("weight"), FieldTypeId.Double, categoryId = examination._id),
    Field("tests.xray", Some("X-ray"), FieldTypeId.String, categoryId = tests._id),
    Field("VISIT_ID", Some("Visit"), FieldTypeId.String, categoryId = visit._id),
    Field("VISIT_TYPE", None, FieldTypeId.String, categoryId = visit._id),
    Field("NOTE", Some(""), FieldTypeId.Text, categoryId = Some(BSONObjectID.generate())) // an unknown category
  )

  private val item = Json.obj(
    "_id" -> BSONObjectID.generate(),
    "PATIENT_ID" -> "P1",
    "AGE" -> 42,
    "WEIGHT" -> 71.5,
    "tests.xray" -> "Ordered",
    "VISIT_ID" -> "V1",
    "VISIT_TYPE" -> JsNull,
    "NOTE" -> "Seen",
    "extra" -> Json.arr(1, "a")
  )

  private def groups(
    categories: Seq[Category] = Seq(visit, tests, examination),
    showFieldStyle: Option[FilterShowFieldStyle.Value] = None
  ) =
    RecordView.fieldGroups(item, fields, categories, ftf, showFieldStyle)

  private def labelsAndFieldNames(groups: Seq[RecordFieldGroup]) =
    groups.map(group => (group.label, group.fields.map(_.name)))

  "The record's groups" should "be one per category in the tree's order, the fields with no category last, without the id and the nulls" in {
    labelsAndFieldNames(groups()) shouldBe Seq(
      (Some("Examination"), Seq("AGE", "WEIGHT")),
      (Some("Examination › Tests"), Seq("tests.xray")),
      (Some("Visit"), Seq("VISIT_ID")),
      (Some(RecordView.OtherFieldsLabel), Seq("extra", "NOTE", "PATIENT_ID"))
    )
  }

  it should "be one with no label if no field has a category" in {
    labelsAndFieldNames(groups(categories = Nil)) shouldBe Seq(
      (None, Seq("AGE", "extra", "NOTE", "PATIENT_ID", "VISIT_ID", "WEIGHT", "tests.xray"))
    )
  }

  it should "show a value with no field by its JSON type" in {
    val Some(extra) = groups().flatMap(_.fields).find(_.name == "extra")

    extra shouldBe RecordField("extra", "extra", false, None, true, ArrayValue(Seq(NumberValue("1"), TextValue("a", false)), 2, false))
    RecordView.typeName(extra) shouldBe "Not in the dictionary"
  }

  it should "end a category path on a parent cycle" in {
    val a = category("a")
    val b = category("b", Some(a))
    a.parentId = b._id

    val Seq(group) = RecordView.fieldGroups(Json.obj("AGE" -> 1), Seq(Field("AGE", categoryId = a._id)), Seq(a, b), ftf)
    group.label shouldBe Some("b › a")
  }

  "A record field's label" should "follow the data set's field style" in {
    def labelsAndShowNames(showFieldStyle: Option[FilterShowFieldStyle.Value]) =
      groups(showFieldStyle = showFieldStyle).flatMap(_.fields).map(field => (field.name, (field.label, field.showName))).toMap

    val default = labelsAndShowNames(None)
    default("AGE") shouldBe (("Age", false))
    default("NOTE") shouldBe (("NOTE", false)) // an empty label

    labelsAndShowNames(Some(FilterShowFieldStyle.NamesOnly))("AGE") shouldBe (("AGE", false))

    val namesAndLabels = labelsAndShowNames(Some(FilterShowFieldStyle.NamesAndLabels))
    namesAndLabels("AGE") shouldBe (("Age", true))
    namesAndLabels("NOTE") shouldBe (("NOTE", false))
  }

  // the record view setting

  "The record's groups by the setting" should "have the listed categories first (a subcategory with its parent), and leave out the hidden fields" in {
    val setting = RecordViewSetting(sectionOrder = Seq("visit", "examination"), hiddenFieldNames = Seq("WEIGHT", "extra"))

    labelsAndFieldNames(RecordView.fieldGroups(item, fields, Seq(visit, tests, examination), ftf, None, setting)) shouldBe Seq(
      (Some("Visit"), Seq("VISIT_ID")),
      (Some("Examination"), Seq("AGE")),
      (Some("Examination › Tests"), Seq("tests.xray")),
      (Some(RecordView.OtherFieldsLabel), Seq("NOTE", "PATIENT_ID"))
    )

    // a listed subcategory before its parent
    labelsAndFieldNames(RecordView.fieldGroups(item, fields, Seq(visit, tests, examination), ftf, None, RecordViewSetting(sectionOrder = Seq("tests")))).map(_._1) shouldBe
      Seq(Some("Examination › Tests"), Some("Examination"), Some("Visit"), Some(RecordView.OtherFieldsLabel))
  }

  it should "be collapsed if listed (exactly the category), identified by the category's id" in {
    val groups = RecordView.fieldGroups(item, fields, Seq(visit, tests, examination), ftf, None, RecordViewSetting(collapsedSections = Seq("examination")))

    groups.map(group => (group.label, group.collapsed)) shouldBe Seq(
      (Some("Examination"), true),
      (Some("Examination › Tests"), false),
      (Some("Visit"), false),
      (Some(RecordView.OtherFieldsLabel), false)
    )
    groups.map(_.key) shouldBe Seq(examination._id.get.stringify, tests._id.get.stringify, visit._id.get.stringify, RecordView.OtherFieldsKey)
  }

  "The record's summary" should "have the fields in the given order, an empty value for a field with none, without the id, the hidden fields and the unknown ones" in {
    val summary = RecordView.summaryFields(
      item,
      Seq("tests.xray", "_id", "AGE", "VISIT_TYPE", "extra", "unknown", "WEIGHT", "AGE"),
      fields,
      ftf,
      None,
      RecordViewSetting(hiddenFieldNames = Seq("WEIGHT"))
    )

    summary.map(field => (field.name, field.label, field.value)) shouldBe Seq(
      ("tests.xray", "X-ray", TextValue("Ordered", false)),
      ("AGE", "Age", NumberValue("42")),
      ("VISIT_TYPE", "VISIT_TYPE", EmptyValue), // a null
      ("extra", "extra", ArrayValue(Seq(NumberValue("1"), TextValue("a", false)), 2, false))
    )
  }

  "The record's title" should "have the fields' display values in place of their names" in {
    val genderItem = Json.obj("PATIENT_ID" -> "P1", "GENDER" -> 1, "EMPTY" -> JsNull)
    val genderFields = Seq(Field("PATIENT_ID"), genderField)

    RecordView.title("Patient {{PATIENT_ID}} ({{ GENDER }})", genderItem, genderFields, ftf) shouldBe Some("Patient P1 (Female)")
    // a missing value is left out, the spaces collapsed
    RecordView.title("Patient  {{MISSING}} {{EMPTY}} {{PATIENT_ID}}", genderItem, genderFields, ftf) shouldBe Some("Patient P1")
    RecordView.title("{{MISSING}}", genderItem, genderFields, ftf) shouldBe None
    // a value with a $ or a \ is kept as it is
    RecordView.title("Code {{CODE}}", Json.obj("CODE" -> "$1\\x"), Nil, ftf) shouldBe Some("Code $1\\x")
  }

  "The data set's sections" should "be its categories in the tree's order, the listed ones first" in {
    val sections = RecordView.sections(Seq(visit, tests, examination))

    sections shouldBe Seq(
      RecordSection(Seq("examination"), Seq("Examination")),
      RecordSection(Seq("examination", "tests"), Seq("Examination", "Tests")),
      RecordSection(Seq("visit"), Seq("Visit"))
    )
    sections(1).label shouldBe "Examination › Tests"

    RecordView.orderedSections(sections, Seq("visit", "unknown")).map(_.name) shouldBe Seq("visit", "examination", "tests")
    RecordView.orderedSections(sections, Nil) shouldBe sections
  }

  "A data set's item name" should "be the display item name, or the data set's name with 'Item'" in {
    val setting = new DataSetSetting("clinic.patients")

    RecordView.itemName("Patients", setting.copy(displayItemName = Some("Patient"))) shouldBe "Patient"
    RecordView.itemName("Patients", setting.copy(displayItemName = Some(" "))) shouldBe "Patients Item"
    RecordView.itemName("Patients", setting) shouldBe "Patients Item"
  }
}
