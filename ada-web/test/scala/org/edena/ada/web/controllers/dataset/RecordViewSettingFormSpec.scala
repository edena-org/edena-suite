package org.edena.ada.web.controllers.dataset

import org.edena.ada.server.models.RecordViewSetting
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.data.Form
import play.api.data.Forms.single

class RecordViewSettingFormSpec extends AnyFlatSpec with Matchers {

  private val form = Form(single("recordView" -> DataSetSettingController.recordViewMapping))

  "The record view form" should "bind the options, trimmed and without duplicates" in {
    val bound = form.bind(Map(
      "recordView.title" -> " Patient {{PATIENT_ID}} ",
      "recordView.summaryFieldNames[0]" -> "PATIENT_ID",
      "recordView.summaryFieldNames[1]" -> " AGE",
      "recordView.summaryFieldNames[2]" -> "PATIENT_ID",
      "recordView.sectionOrder[0]" -> "visit",
      "recordView.sectionOrder[1]" -> "demographics",
      "recordView.collapsedSections[0]" -> "raw data",
      "recordView.hiddenFieldNames[0]" -> "embedding",
      "recordView.hiddenFieldNames[1]" -> " "
    ))

    bound.errors shouldBe Nil
    bound.get shouldBe Some(RecordViewSetting(
      Some("Patient {{PATIENT_ID}}"), Seq("PATIENT_ID", "AGE"), Seq("visit", "demographics"), Seq("raw data"), Seq("embedding")
    ))
  }

  it should "be none with no option set (the defaults)" in {
    form.bind(Map.empty[String, String]).get shouldBe None
    form.bind(Map("recordView.title" -> "  ", "recordView.hiddenFieldNames[0]" -> "")).get shouldBe None
  }

  it should "fill the options back (for the edit page)" in {
    val setting = RecordViewSetting(Some("Patient"), Seq("AGE"), Nil, Seq("raw data"), Seq("embedding"))
    val filled = form.fill(Some(setting))

    filled("recordView.title").value shouldBe Some("Patient")
    filled("recordView.summaryFieldNames[0]").value shouldBe Some("AGE")
    filled("recordView.collapsedSections[0]").value shouldBe Some("raw data")
    form.bind(filled.data).get shouldBe Some(setting)
  }
}
