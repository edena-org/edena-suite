package field

import java.util.Date

import org.edena.ada.server.models.dataimport.CsvDataSetImport
import org.edena.ada.server.models.{DataSetSetting, StorageType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.bson.BSONObjectID

// the format of CsvDataSetImport is hand-written (23 fields, over the macro limit) - it must behave as the macro one
class CsvDataSetImportFormatTest extends AnyFlatSpec with Matchers {

  private val format = CsvDataSetImport.format

  private val fullImport = CsvDataSetImport(
    _id = Some(BSONObjectID.generate()),
    dataSpaceName = "space",
    dataSetId = "space.data",
    dataSetName = "Data",
    path = Some("/data/file.csv"),
    delimiter = ";",
    eol = Some("\n"),
    charsetName = Some("UTF-8"),
    matchQuotes = true,
    inferFieldTypes = true,
    inferenceMaxEnumValuesCount = Some(15),
    inferenceMinAvgValuesPerEnum = Some(2.5),
    inferenceMinTextLength = Some(80),
    arrayDelimiter = Some("|"),
    booleanIncludeNumbers = true,
    explicitNullAliases = Seq("na", "-"),
    saveBatchSize = Some(100),
    setting = Some(new DataSetSetting("space.data", StorageType.ElasticSearch)),
    timeCreated = new Date(1700000000000L),
    timeLastExecuted = Some(new Date(1700000100000L))
  )

  "CSV data set import format" should "round-trip all the fields" in {
    val json = format.writes(fullImport)

    (json \ "inferenceMinTextLength").as[Int] should be (80)
    format.reads(json).get should be (fullImport)
  }

  it should "not write empty options (as the macro format)" in {
    val minimalImport = CsvDataSetImport(
      dataSpaceName = "space", dataSetId = "space.data", dataSetName = "Data",
      delimiter = ",", matchQuotes = false, inferFieldTypes = true, timeCreated = new Date(1700000000000L)
    )
    val json = format.writes(minimalImport)

    json.keys should contain noneOf ("_id", "path", "eol", "inferenceMinTextLength", "setting", "timeLastExecuted")
    format.reads(json).get should be (minimalImport)
  }

  it should "read imports stored before the min. text length was introduced" in {
    val storedJson = format.writes(fullImport).as[JsObject] - "inferenceMinTextLength"

    format.reads(storedJson).get should be (fullImport.copy(inferenceMinTextLength = None))
  }

  it should "require the non-optional fields" in {
    val withoutDelimiter = format.writes(fullImport).as[JsObject] - "delimiter"

    format.reads(withoutDelimiter).isError should be (true)
    format.reads(Json.obj()).isError should be (true)
  }
}
