package field

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsString, Json}

import scala.concurrent.Await
import scala.concurrent.duration._

class TextFieldTypeInferrerTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("TextFieldTypeInferrerTest")
  private implicit val materializer: Materializer = Materializer(system)

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 30.seconds)
    super.afterAll()
  }

  private val fti = FieldTypeHelper.fieldTypeInferrer
  private val jsonFti = FieldTypeHelper.jsonFieldTypeInferrer

  private val string = FieldTypeSpec(FieldTypeId.String)
  private val text = FieldTypeSpec(FieldTypeId.Text)

  // enough distinct short values so that the field isn't inferred as an enum
  private val shortValues = (1 to 30).map(i => s"value $i")

  private def ofLength(length: Int) = "x" * length

  private def inferred(values: Seq[String]) = {
    val batchSpec = fti(values).spec
    // the streaming (single-pass) inference must agree with the batch one
    val streamSpec = Await.result(fti(Source(values.toList)), 30.seconds).spec
    streamSpec should be (batchSpec)
    batchSpec
  }

  "Text field type" should "be inferred if the longest value reaches the min. text length (50 by default)" in {
    FieldTypeHelper.minTextLength should be (50)

    inferred(shortValues) should be (string)
    inferred(shortValues :+ ofLength(49)) should be (string)
    inferred(shortValues :+ ofLength(50)) should be (text)
    inferred(shortValues :+ ofLength(5000)) should be (text)
  }

  it should "be decided by the longest value only, regardless of how many values are long" in {
    val prose = "The quick brown fox jumps over the lazy dog while the band keeps playing in the park."

    inferred(shortValues ++ Seq(prose, "na", "")) should be (text)
    inferred(prose +: (1 to 1000).map(i => s"$i")) should be (text)
  }

  it should "ignore null aliases" in {
    inferred(Seq("na", "", "null", "N/A")) should be (FieldTypeSpec(FieldTypeId.Null))
    inferred(shortValues ++ Seq.fill(10)("n/a")) should be (string)
  }

  it should "give way to enum for a few distinct (even long) values" in {
    val longCategories = Seq(ofLength(60), ofLength(70) + "y", ofLength(80) + "z")
    val spec = inferred(Seq.fill(10)(longCategories).flatten)

    spec.fieldType should be (FieldTypeId.Enum)
    spec.enumValues.values.toSet should be (longCategories.toSet)
  }

  it should "give way to numbers, booleans, and dates" in {
    // too long for a Long but still a (Double) number
    inferred(Seq("1" * 60, "2" * 60, "na")) should be (FieldTypeSpec(FieldTypeId.Double))
    inferred(Seq("2016-08-10 20:45:12", "na")) should be (FieldTypeSpec(FieldTypeId.Date))
    inferred(Seq("true", "false", "true")) should be (FieldTypeSpec(FieldTypeId.Boolean))
  }

  it should "respect a custom min. text length" in {
    val customFti = FieldTypeHelper.fieldTypeInferrerFactory(minTextLength = 10).ofString

    customFti(shortValues).spec should be (string) // "value 30" has 8 chars
    customFti(shortValues :+ "ten chars!").spec should be (text)
  }

  it should "be inferred from JSON values (strings and arrays of strings)" in {
    val shortJsons = shortValues.map(JsString(_))

    jsonFti(shortJsons).spec should be (string)
    jsonFti(shortJsons :+ JsString(ofLength(50))).spec should be (text)
    jsonFti(shortJsons ++ Seq(JsNull, JsString(ofLength(120)))).spec should be (text)

    // an array of strings with a long element is a Text array
    val arrays = shortValues.grouped(3).map(group => JsArray(group.map(JsString(_)))).toSeq
    jsonFti(arrays).spec should be (FieldTypeSpec(FieldTypeId.String, isArray = true))
    jsonFti(arrays :+ Json.arr(ofLength(50), "short")).spec should be (FieldTypeSpec(FieldTypeId.Text, isArray = true))

    // numbers stay numbers, JSON objects stay JSON
    jsonFti(Seq(JsNumber(1), JsNumber(2))).spec should be (FieldTypeSpec(FieldTypeId.Integer))
    jsonFti(Seq(Json.obj("note" -> ofLength(100)))).spec should be (FieldTypeSpec(FieldTypeId.Json))
  }
}
