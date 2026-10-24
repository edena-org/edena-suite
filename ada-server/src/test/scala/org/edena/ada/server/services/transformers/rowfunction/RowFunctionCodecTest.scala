package org.edena.ada.server.services.transformers.rowfunction

import java.text.SimpleDateFormat

import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models.Field
import org.edena.core.field.FieldTypeId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsNull, Json}

class RowFunctionCodecTest extends AnyFlatSpec with Matchers {

  private val outFields = Seq(
    Field("score", fieldType = FieldTypeId.Double),
    Field("count", fieldType = FieldTypeId.Integer),
    Field("color", fieldType = FieldTypeId.Enum, enumValues = Map("0" -> "red", "1" -> "green")),
    Field("flag", fieldType = FieldTypeId.Boolean),
    Field("when", fieldType = FieldTypeId.Date),
    Field("name", fieldType = FieldTypeId.String),
    Field("values", fieldType = FieldTypeId.Double, isArray = true)
  )

  private val codec = new RowFunctionCodec(
    RowFunctionFields(Seq(Field("a"), Field("b")), outFields),
    FieldTypeHelper.fieldTypeFactory()
  )

  private val output = Json.obj(
    "score" -> 3.5,
    "count" -> 7,
    "color" -> 1,
    "flag" -> true,
    "when" -> 1700000000000L,
    "name" -> "x",
    "values" -> Json.arr(1, 2.5)
  )

  "Row function codec" should "project a row to the in fields, an undefined value as null" in {
    codec.encode(Json.obj("_id" -> "id", "a" -> 1, "c" -> 3)) shouldBe Json.obj("a" -> 1, "b" -> JsNull)
  }

  it should "decode stored values as they are and ignore other fields" in {
    codec.decode(output + ("other" -> Json.toJson("y"))) shouldBe Right(output)
  }

  it should "convert display values to the stored ones" in {
    val displayOutput = Json.obj(
      "score" -> "3.5",
      "count" -> "7",
      "color" -> "green",
      "flag" -> "true",
      "when" -> "2026-01-02",
      "name" -> 5,
      "values" -> Json.arr(1, "2.5", JsNull)
    )

    val day = new SimpleDateFormat("yyyy-MM-dd").parse("2026-01-02").getTime

    codec.decode(displayOutput) shouldBe Right(
      Json.obj(
        "score" -> 3.5,
        "count" -> 7,
        "color" -> 1,
        "flag" -> true,
        "when" -> day,
        "name" -> "5",
        "values" -> Json.arr(1, 2.5, JsNull)
      )
    )
  }

  it should "accept null values" in {
    val nullOutput = outFields.foldLeft(Json.obj())((json, field) => json + (field.name -> JsNull))

    codec.decode(nullOutput) shouldBe Right(nullOutput)
  }

  it should "reject an output without all the out fields" in {
    val error = codec.decode(output - "count" - "flag").left.toOption.get

    error should include("count, flag")
  }

  it should "reject an invalid value" in {
    codec.decode(output + ("color" -> Json.toJson("blue"))).left.toOption.get should include(
      "The value \"blue\" of the out field 'color' isn't a valid Enum"
    )
    codec.decode(output + ("score" -> Json.toJson("high"))).left.toOption.get should include("'score'")
    codec.decode(output + ("values" -> Json.toJson("1, x"))).left.toOption.get should include("valid Double array")
  }

  it should "tell whether a row has all the out values" in {
    codec.hasOutValues(Json.obj("a" -> 1) ++ output) shouldBe true
    codec.hasOutValues(output + ("count" -> JsNull)) shouldBe false
    codec.hasOutValues(output - "flag") shouldBe false
    codec.hasOutValues(Json.obj("a" -> 1)) shouldBe false
  }
}
