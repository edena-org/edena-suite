package org.edena.ada.server.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsNull, Json}
import reactivemongo.api.bson.BSONObjectID

class DataSetSettingFormatTest extends AnyFlatSpec with Matchers {

  private implicit val format = DataSetFormattersAndIds.dataSetSettingFormat

  private val recordView = RecordViewSetting(
    title = Some("Patient {{PATIENT_ID}}"),
    summaryFieldNames = Seq("PATIENT_ID", "AGE"),
    sectionOrder = Seq("demographics", "visit"),
    collapsedSections = Seq("raw"),
    hiddenFieldNames = Seq("embedding")
  )

  private val setting = new DataSetSetting("clinic.patients", StorageType.ElasticSearch).copy(
    _id = Some(BSONObjectID.generate()),
    displayItemName = Some("Patient"),
    recordView = Some(recordView)
  )

  "A data set setting" should "be read as written, with its record view" in {
    Json.toJson(setting).as[DataSetSetting] shouldBe setting
    Json.toJson(setting.copy(recordView = None)).as[DataSetSetting] shouldBe setting.copy(recordView = None)
  }

  it should "be read from a document written before the record view (no migration)" in {
    val document = Json.toJsObject(setting) - "recordView"

    document.as[DataSetSetting].recordView shouldBe None
    (document + ("recordView" -> JsNull)).as[DataSetSetting].recordView shouldBe None
  }

  it should "take the defaults of the record view's options not in a document" in {
    val document = Json.toJsObject(setting) + ("recordView" -> Json.obj("collapsedSections" -> Seq("raw")))

    document.as[DataSetSetting].recordView shouldBe Some(RecordViewSetting(collapsedSections = Seq("raw")))
  }

  it should "keep its record view in the cache's POJO (which has no such field)" in {
    DataSetSetting.fromPOJO(DataSetSetting.toPOJO(setting)) shouldBe setting
    DataSetSetting.fromPOJO(DataSetSetting.toPOJO(setting.copy(recordView = None))).recordView shouldBe None
  }

  "A record view setting" should "be empty with no option set" in {
    RecordViewSetting().isEmpty shouldBe true
    RecordViewSetting(hiddenFieldNames = Seq("embedding")).isEmpty shouldBe false
    recordView.isEmpty shouldBe false
  }
}
