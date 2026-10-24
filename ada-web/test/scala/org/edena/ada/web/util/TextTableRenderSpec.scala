package org.edena.ada.web.util

import org.edena.ada.server.models.Field
import org.edena.ada.web.util.FieldTypeFullRenderer.{TableTextMaxLength, shortenText}
import org.edena.core.field.FieldTypeId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.libs.json.{JsString, JsValue}

class TextTableRenderSpec extends AnyFlatSpec with Matchers {

  private implicit val configuration: Configuration = Configuration.empty

  private def render(field: Field, value: Option[JsValue]) = {
    val renderer = FieldTypeFullRenderer(field)
    renderer((value, field.name, None)).body
  }

  private val textField = Field("note", fieldType = FieldTypeId.Text)
  private val stringField = Field("code", fieldType = FieldTypeId.String)

  "A Text value in a table" should "be cut after the max. length, with '...'" in {
    val long = "x" * 500
    render(textField, Some(JsString(long))) shouldBe ("x" * TableTextMaxLength) + "..."
  }

  it should "be kept whole up to the max. length" in {
    val exact = "y" * TableTextMaxLength
    render(textField, Some(JsString(exact))) shouldBe exact
    render(textField, Some(JsString("short"))) shouldBe "short"
    render(textField, None) shouldBe ""
  }

  it should "be HTML-escaped, so a cut can't break the page" in {
    val html = "<b>bold</b> " + ("a" * 300)
    val rendered = render(textField, Some(JsString(html)))
    rendered should startWith ("&lt;b&gt;bold&lt;/b&gt; ")
    rendered should endWith ("...")
    rendered should not include ("<b>")
  }

  it should "not split a surrogate pair at the cut" in {
    val emoji = "😀" // one emoji, two chars
    val text = ("z" * (TableTextMaxLength - 1)) + emoji + "tail"
    shortenText(text) shouldBe ("z" * (TableTextMaxLength - 1)) + "..."
    shortenText(text, TableTextMaxLength + 1) shouldBe ("z" * (TableTextMaxLength - 1)) + emoji + "..."
  }

  "A String value in a table" should "be rendered as before (not cut)" in {
    val long = "s" * 500
    render(stringField, Some(JsString(long))) shouldBe long
  }
}
