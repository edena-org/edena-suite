package scala.org.edena.play.util

import org.edena.ada.server.models.Translation
import org.edena.play.util.JsonBodyUtil
import org.scalatest._
import play.api.libs.json._
import reactivemongo.api.bson.BSONObjectID

/**
 * Tests for the pure JSON/JSONL request-body parsing behind the generic CRUD controllers' JSON
 * save/update paths (lives in the `play` module, which has no test infra of its own).
 */
class JsonBodyUtilSpec extends FlatSpec with Matchers {

  private val id = BSONObjectID.parse("5dc029c50c00000f059e1ccd").get

  private def translationJson(original: String, translated: String = "prelozene") =
    Json.obj("original" -> original, "translated" -> translated)

  behavior of "JsonBodyUtil.validateItems"

  it should "parse a single JSON object as one item" in {
    val result = JsonBodyUtil.validateItems[Translation](translationJson("hello"))

    result.isRight shouldBe true
    val items = result.toOption.get
    items should have size 1
    items.head.original shouldBe "hello"
  }

  it should "parse a JSON array as multiple items" in {
    val json = JsArray(Seq(translationJson("a"), translationJson("b"), translationJson("c")))
    val result = JsonBodyUtil.validateItems[Translation](json)

    result.toOption.get.map(_.original) shouldBe List("a", "b", "c")
  }

  it should "round-trip an $oid id through the entity Format" in {
    val json = translationJson("hello") + ("_id" -> Json.obj("$oid" -> id.stringify))
    val result = JsonBodyUtil.validateItems[Translation](json)

    result.toOption.get.head._id shouldBe Some(id)
  }

  it should "reject an array with an invalid element, naming its index, and yield no items" in {
    val bad = Json.obj("wrong" -> "shape")
    val json = JsArray(Seq(translationJson("ok"), bad))
    val result = JsonBodyUtil.validateItems[Translation](json)

    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors should have size 1
    errors.head.index shouldBe 1
    errors.head.line shouldBe None
    // the payload is a JsError.toJson object naming the missing path
    errors.head.errors.toString should include("error.path.missing")
  }

  behavior of "JsonBodyUtil.parseJsonl"

  it should "parse one JSON per line, skipping blank lines" in {
    val text =
      s"""${translationJson("a")}
         |
         |${translationJson("b")}
         |   ${" "}
         |${translationJson("c")}""".stripMargin

    val result = JsonBodyUtil.parseJsonl[Translation](text)
    result.toOption.get.map(_.original) shouldBe List("a", "b", "c")
  }

  it should "report an unparseable line with its 1-based line number" in {
    val text =
      s"""${translationJson("a")}
         |this is not json
         |${translationJson("c")}""".stripMargin

    val result = JsonBodyUtil.parseJsonl[Translation](text)

    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors should have size 1
    errors.head.line shouldBe Some(2)
  }

  it should "carry the line number into validation errors" in {
    val text =
      s"""${translationJson("a")}
         |
         |{"wrong": "shape"}""".stripMargin

    val result = JsonBodyUtil.parseJsonl[Translation](text)

    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors should have size 1
    errors.head.line shouldBe Some(3)
    errors.head.errors.toString should include("error.path.missing")
  }

  behavior of "JsonBodyUtil.errorsToJson"

  it should "produce a message and one entry per error with index and optional line" in {
    val errors = List(
      JsonBodyUtil.JsonItemError(0, None, JsString("bad")),
      JsonBodyUtil.JsonItemError(2, Some(5), JsString("worse"))
    )
    val json = JsonBodyUtil.errorsToJson("Translation", errors)

    (json \ "message").as[String] should include("Translation")
    val errorArray = (json \ "errors").as[Seq[JsObject]]
    errorArray should have size 2
    (errorArray.head \ "index").as[Int] shouldBe 0
    (errorArray.head \ "line").toOption shouldBe None
    (errorArray(1) \ "index").as[Int] shouldBe 2
    (errorArray(1) \ "line").as[Int] shouldBe 5
  }
}
