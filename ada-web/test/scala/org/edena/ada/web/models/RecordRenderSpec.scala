package org.edena.ada.web.models

import org.edena.ada.web.models.RecordValue._
import org.edena.core.field.FieldTypeId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.Call
import reactivemongo.api.bson.BSONObjectID

class RecordRenderSpec extends AnyFlatSpec with Matchers {

  private def render(value: RecordValue) = views.html.dataset.recordValue(value).body

  private def count(text: String, html: String) = text.r.findAllMatchIn(html).size

  "A rendered record value" should "be HTML-escaped" in {
    val html = render(TextValue("<script>alert(1)</script>", false))
    html should include("&lt;script&gt;alert(1)&lt;/script&gt;")
    html should not include("<script>")

    render(EnumValue("<b>Female</b>", "\"1\"")) should (include("&lt;b&gt;Female&lt;/b&gt;") and include("title=\"Stored value: &quot;1&quot;\""))
    render(InvalidValue("\"<i>\"", "Not a valid Integer value.")) should include("<code>&quot;&lt;i&gt;&quot;</code>")
    render(JsonValue("{\"a\": \"</pre>\"}", false)) should include("&lt;/pre&gt;")
  }

  it should "cut a long text or JSON at first, with 'Show more'" in {
    render(TextValue("long", true)) should (include("record-text record-collapsible") and include("Show more"))
    render(JsonValue("{}", true)) should (include("record-json record-collapsible") and include("Show more"))

    render(TextValue("short", false)) should not include("Show more")
    render(TextValue("short", false)) should not include("record-collapsible")
  }

  it should "show an empty text and a null as such" in {
    render(TextValue("  ", false)) should include("<span class=\"record-empty\">empty</span>")
    render(NullValue) should include("<span class=\"record-empty\">null</span>")
  }

  it should "show the first elements of an array, the others with 'Show all'" in {
    def numbers(count: Int) = (1 to count).map(i => NumberValue(i.toString))

    val html = render(ArrayValue(numbers(25), 25, false))
    count("record-chip ", html) shouldBe 25
    count("record-extra", html) shouldBe 25 - RecordView.ArrayPreviewSize
    html should include("Show all 25")

    render(ArrayValue(numbers(20), 20, false)) should not include("Show all")
    render(ArrayValue(numbers(2), 2, true)) should (include("<ol class=\"record-list\">") and not include("record-chip"))
    render(ArrayValue(Nil, 0, false)) should include("empty array")
  }

  it should "note an array shown in part" in {
    render(ArrayValue(Seq(NumberValue("1")), 5000, false)) should include("The first 1 of 5000 values are shown.")
  }

  it should "be a link opening a new tab" in {
    val html = render(LinkValue("https://example.org/a?b=1&c=2"))
    html should include("href=\"https://example.org/a?b=1&amp;c=2\"")
    html should include("target=\"_blank\" rel=\"noopener noreferrer\"")
  }

  "A rendered record" should "have a section per group, its fields with their types and a summary at the top" in {
    val age = RecordField("AGE", "Age", false, Some(FieldTypeId.Integer), false, NumberValue("42"))
    val patientId = RecordField("PATIENT_ID", "Patient ID", true, Some(FieldTypeId.String), false, TextValue("P1", false))
    val id = BSONObjectID.generate()

    val html = views.html.dataset.recordContent(
      "Patient P1",
      id,
      Seq(patientId, age.copy(value = EmptyValue)),
      Seq(RecordFieldGroup(Some("Examination"), Seq(age), "examinationId"), RecordFieldGroup(Some("Other fields"), Seq(patientId))),
      Call("GET", "/dataSets/getDefaultView?data_set=clinic.patients"),
      "clinic.patients"
    ).body

    html should include("<h3 class=\"record-title\">Patient P1</h3>")
    count("<section class=\"record-group\"", html) shouldBe 2
    html should include("data-key=\"examinationId\"")
    html should include("<span class=\"bi bi-chevron-down record-group-chevron\"></span>Examination<span class=\"record-group-count\">1</span>")
    html should include("data-search=\"age age\"")
    html should include("<span class=\"bi bi-123 record-type-icon\" title=\"Integer\"></span>")
    html should include("<span>Patient ID<span class=\"record-name\">PATIENT_ID</span></span>")
    html should include(id.stringify)
    html should include("data-count=\"2\">2 fields</span>")
    html should include("data-storage-key=\"clinic.patients\"")
    html should include("href=\"/dataSets/getDefaultView?data_set=clinic.patients\"")

    // the summary
    """<dt class="record-summary-label" title="PATIENT_ID">Patient ID</dt>\s*<dd class="record-summary-value">\s*<div class="record-text">P1</div>""".r.findFirstIn(html) shouldBe defined
    """<dt class="record-summary-label" title="AGE">Age</dt>\s*<dd class="record-summary-value">\s*<span class="record-empty" title="No value">—</span>""".r.findFirstIn(html) shouldBe defined
  }

  it should "have a collapsed section collapsed, and no summary with no summary fields" in {
    val age = RecordField("AGE", "Age", false, Some(FieldTypeId.Integer), false, NumberValue("42"))

    val html = views.html.dataset.recordContent(
      "Patient", BSONObjectID.generate(), Nil, Seq(RecordFieldGroup(Some("Examination"), Seq(age), "examinationId", collapsed = true)), Call("GET", "/"), "clinic.patients"
    ).body

    html should include("<section class=\"record-group collapsed\" data-key=\"examinationId\">")
    html should include("aria-expanded=\"false\"")
    html should not include("record-summary")
  }

  it should "say if the record has no values" in {
    val html = views.html.dataset.recordContent("Patient", BSONObjectID.generate(), Nil, Nil, Call("GET", "/"), "clinic.patients").body

    html should include("The record has no values.")
    html should not include("id=\"recordFieldSearch\"")
  }
}
