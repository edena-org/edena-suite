package org.edena.ada.server.services.transformers.rowfunction

import org.edena.ada.server.AdaException
import org.edena.ada.server.models.Field
import org.edena.core.field.FieldTypeId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reactivemongo.api.bson.BSONObjectID

class RowFunctionFieldsTest extends AnyFlatSpec with Matchers {

  private val categoryId = BSONObjectID.generate()

  private val dictionary = Seq(
    Field("a", fieldType = FieldTypeId.Integer),
    Field("b", label = Some("B"), fieldType = FieldTypeId.Double),
    Field("color", fieldType = FieldTypeId.Enum, enumValues = Map("0" -> "red"), categoryId = Some(categoryId)),
    Field("tags", fieldType = FieldTypeId.String, isArray = true)
  )

  private def resolve(inFieldNames: Seq[String], outFields: Seq[Field]) =
    RowFunctionFields.resolve(inFieldNames, outFields, dictionary)

  private def error(inFieldNames: Seq[String], outFields: Seq[Field]) =
    the[AdaException] thrownBy resolve(inFieldNames, outFields)

  "Row function fields" should "take the in fields from the dictionary and new out fields as they are" in {
    val sum = Field("sum", label = Some("Sum"), fieldType = FieldTypeId.Double)

    val fields = resolve(Seq("b", "a"), Seq(sum))

    fields.inFields shouldBe Seq(dictionary(1), dictionary(0))
    fields.inFieldNames shouldBe Seq("b", "a")
    fields.outFields shouldBe Seq(sum)
  }

  it should "keep an existing out field, extending its enum values and adding a missing label" in {
    val color = Field("color", label = Some("Color"), fieldType = FieldTypeId.Enum, enumValues = Map("0" -> "rot", "1" -> "green"))

    val fields = resolve(Seq("a"), Seq(color))

    fields.outFields shouldBe Seq(
      Field(
        "color",
        label = Some("Color"),
        fieldType = FieldTypeId.Enum,
        enumValues = Map("0" -> "rot", "1" -> "green"),
        categoryId = Some(categoryId)
      )
    )
  }

  it should "keep an existing out field's label" in {
    val fields = resolve(Seq("a"), Seq(Field("b", label = Some("Other"), fieldType = FieldTypeId.Double)))

    fields.outFields.head.label shouldBe Some("B")
  }

  it should "reject a type change of an existing field" in {
    error(Seq("a"), Seq(Field("b", fieldType = FieldTypeId.String))).getMessage should include("existing field is Double")
    error(Seq("a"), Seq(Field("tags", fieldType = FieldTypeId.String))).getMessage should include("String array")
  }

  it should "reject missing, unknown or duplicate fields, and an _id out field" in {
    val sum = Field("sum", fieldType = FieldTypeId.Double)

    error(Nil, Seq(sum)).getMessage should include("No in field")
    error(Seq("a"), Nil).getMessage should include("No out field")
    error(Seq("a", "x", "y"), Seq(sum)).getMessage should include("not found in the data set: x, y")
    error(Seq("a", "b", "a"), Seq(sum)).getMessage should include("Duplicate in field(s): a")
    error(Seq("a"), Seq(sum, sum)).getMessage should include("Duplicate out field(s): sum")
    error(Seq("a"), Seq(Field("_id"))).getMessage should include("'_id' can't be an out field")
  }
}
