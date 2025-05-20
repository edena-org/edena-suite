package org.edena.json.util

import org.scalatest.{AsyncFlatSpec, Matchers}
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, Json}

class JsonHelperTest extends AsyncFlatSpec with Matchers with JsonHelper {

  "JsonHelper" should "serialize int correctly" in {
    val json = toJson(2)

    json should not be null
    json shouldBe a[JsNumber]
    json shouldBe JsNumber(2)
  }

  it should "serialize double correctly" in {
    val json = toJson(2.0)

    json should not be null
    json shouldBe a[JsNumber]
    json shouldBe JsNumber(2.0)
  }

  it should "serialize long correctly" in {
    val json = toJson(2L)

    json should not be null
    json shouldBe a[JsNumber]
    json shouldBe JsNumber(2L)
  }

  it should "serialize float correctly" in {
    val json = toJson(2.0f)

    json should not be null
    json shouldBe a[JsNumber]
    json shouldBe JsNumber(2.0f)
  }

  it should "serialize BigDecimal correctly" in {
    val json = toJson(BigDecimal(2.0))

    json should not be null
    json shouldBe a[JsNumber]
    json shouldBe JsNumber(2.0)
  }

  it should "serialize BigInt correctly" in {
    val json = toJson(BigInt(2))

    json should not be null
    json shouldBe a[JsNumber]
    json shouldBe JsNumber(2)
  }

  it should "serialize String correctly" in {
    val json = toJson("test")

    json should not be null
    json shouldBe a[JsString]
    json shouldBe JsString("test")
  }

  it should "serialize Boolean correctly" in {
    val json = toJson(true)

    json should not be null
    json shouldBe a[JsBoolean]
    json shouldBe JsBoolean(true)
  }

  it should "serialize null correctly" in {
    val json = toJson(null)

    json should not be null
    json shouldBe JsNull
  }

  it should "serialize empty list correctly" in {
    val json = toJson(List())

    json should not be null
    json shouldBe a[JsArray]
    json shouldBe JsArray()
  }

  it should "serialize non-empty list correctly" in {
    val json = toJson(List(2.5, 9.1))

    json should not be null
    json shouldBe a[JsArray]
    json shouldBe JsArray(Seq(JsNumber(2.5), JsNumber(9.1)))
  }

  it should "serialize empty seq correctly" in {
    val json = toJson(Seq())

    json should not be null
    json shouldBe a[JsArray]
    json shouldBe JsArray()
  }

  it should "serialize non-empty seq correctly" in {
    val json = toJson(Seq(2.5, 9.1))

    json should not be null
    json shouldBe a[JsArray]
    json shouldBe JsArray(Seq(JsNumber(2.5), JsNumber(9.1)))
  }

  it should "serialize empty set correctly" in {
    val json = toJson(Set())

    json should not be null
    json shouldBe a[JsArray]
    json shouldBe JsArray()
  }

  it should "serialize non-empty set correctly" in {
    val json = toJson(Set(2.5, 9.1))

    json should not be null
    json shouldBe a[JsArray]
    json shouldBe JsArray(Seq(JsNumber(2.5), JsNumber(9.1)))
  }

  it should "serialize empty seq (base) correctly" in {
    val json = toJson(scala.collection.Seq())

    json should not be null
    json shouldBe a[JsArray]
    json shouldBe JsArray()
  }

  it should "serialize non-empty seq (base) correctly" in {
    val json = toJson(scala.collection.Seq(2.5, 9.1))

    json should not be null
    json shouldBe a[JsArray]
    json shouldBe JsArray(Seq(JsNumber(2.5), JsNumber(9.1)))
  }

  it should "serialize empty map correctly" in {
    val json = toJson(Map())

    json should not be null
    json shouldBe a[JsObject]
    json shouldBe Json.obj()
  }

  it should "serialize non-empty map correctly" in {
    val json = toJson(Map("a" -> 2.5, "b" -> 9.1))

    json should not be null
    json shouldBe a[JsObject]
    json shouldBe Json.obj("a" -> JsNumber(2.5), "b" -> JsNumber(9.1))
  }

  it should "serialize nested map correctly" in {
    val json = toJson(
      Map(
        "a" ->
          Map(
            "b" -> 2.5,
            "x" -> true
          ),
        "c" ->
          Map(
            "d" -> 9.1,
            "e" -> Seq(JsString("test"), JsNumber(2.5))
          )
      )
    )

    json should not be null
    json shouldBe a[JsObject]
    json shouldBe Json.obj(
      "a" -> Json.obj(
        "b" -> JsNumber(2.5),
        "x" -> JsBoolean(true)
      ),
      "c" -> Json.obj(
        "d" -> JsNumber(9.1),
        "e" -> JsArray(Seq(JsString("test"), JsNumber(2.5)))
      )
    )
  }
}
