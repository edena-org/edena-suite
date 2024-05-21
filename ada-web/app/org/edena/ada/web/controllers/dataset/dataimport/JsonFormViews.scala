package org.edena.ada.web.controllers.dataset.dataimport

import org.edena.ada.server.models.dataimport.JsonDataSetImport
import org.edena.ada.server.models.{DataSetSetting, StorageType}
import org.edena.play.controllers.WebContext
import org.edena.play.formatters.SeqFormatter
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import views.html.{datasetimport => view}

object JsonFormViews extends DataSetImportFormViews[JsonDataSetImport] {

  override protected val imagePath = Some("images/logos/json_100.png")

  private implicit val stringSeqFormatter = SeqFormatter(nonEmptyStringsOnly = false)

  override protected val extraMappings = Seq(
    "inferenceMaxEnumValuesCount" -> optional(number(min = 1)),
    "inferenceMinAvgValuesPerEnum" -> optional(of[Double]).verifying("Must be positive", _.map(_ > 0).getOrElse(true)),
    "saveBatchSize" -> optional(number(min = 1)),
    "explicitNullAliases" -> of[Seq[String]]
  )

  override protected val viewElements =
    view.jsonTypeElements(_: Form[JsonDataSetImport])(_: WebContext)

  override protected val defaultCreateInstance =
    Some(() => JsonDataSetImport(
      dataSpaceName = "",
      dataSetId = "",
      dataSetName = "",
      inferFieldTypes = true,
      saveBatchSize = Some(10),
      setting = Some(new DataSetSetting("", StorageType.ElasticSearch))
    ))
}
