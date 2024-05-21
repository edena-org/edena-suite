package org.edena.ada.web.controllers.dataset.dataimport

import java.util.Date
import org.edena.ada.server.models._
import org.edena.ada.server.models.dataimport.{CsvDataSetImport, DataSetImport}
import org.edena.ada.web.controllers.MappingHelper
import org.edena.ada.web.controllers.core.GenericMapping
import org.edena.core.util.{firstCharToLowerCase, hasNonAlphanumericUnderscore}
import org.edena.core.util.toHumanReadableCamel
import org.edena.play.controllers.{CreateEditFormViews, IdForm, WebContext}
import org.edena.play.formatters.{EnumFormatter, MapJsonFormatter, SeqFormatter}
import org.edena.store.json.JsObjectIdentity
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.twirl.api.Html
import reactivemongo.api.bson.BSONObjectID
import views.html.layout
import views.html.{datasetimport => view}

import scala.collection.Traversable
import scala.reflect.runtime.universe.{TypeTag, typeOf}

abstract protected[controllers] class DataSetImportFormViews[E <: DataSetImport: TypeTag](
  implicit manifest: Manifest[E]
) extends CreateEditFormViews[E, BSONObjectID] with MappingHelper {

  private val domainNameSuffix = "DataSetImport"
  private val humanReadableSuffix = toHumanReadableCamel(domainNameSuffix)
  protected[controllers] val displayName: String = toHumanReadableCamel(simpleClassName.replaceAllLiterally(domainNameSuffix, ""))
  private val className =  manifest.runtimeClass.getName

  protected val imagePath: Option[String] = None
  protected val imageLink: Option[String] = None

  protected implicit val seqFormatter = SeqFormatter()
  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val filterShowFieldStyleFormatter = EnumFormatter(FilterShowFieldStyle)
  private implicit val storageTypeFormatter = EnumFormatter(StorageType)
  private implicit val widgetGenerationMethodFormatter = EnumFormatter(WidgetGenerationMethod)

  protected val dataSetSettingMapping: Mapping[DataSetSetting] = mapping(
    "id" -> ignored(Option.empty[BSONObjectID]),
    "dataSetId" -> nonEmptyText,
    "keyFieldName" -> default(nonEmptyText, JsObjectIdentity.name),
    "exportOrderByFieldName" -> optional(text),
    "defaultScatterXFieldName" -> optional(text),
    "defaultScatterYFieldName" -> optional(text),
    "defaultDistributionFieldName" -> optional(text),
    "defaultCumulativeCountFieldName" -> optional(text),
    "filterShowFieldStyle" -> optional(of[FilterShowFieldStyle.Value]),
    "filterShowNonNullCount" -> boolean,
    "displayItemName" -> optional(text),
    "storageType" -> of[StorageType.Value],
    "mongoAutoCreateIndexForProjection" -> boolean,
    "cacheDataSet" -> ignored(false),
    "ownerId" -> ignored(Option.empty[BSONObjectID]),
    "showSideCategoricalTree" -> boolean,
    "extraNavigationItems" -> ignored(Seq[NavigationItem]()),
    "customControllerClassName" -> optional(text),
    "description" -> optional(text),
    "widgetEngineClassName" -> optional(text),
    "customStorageCollectionName" -> optional(text)
  )(DataSetSetting.apply)(DataSetSetting.unapply)

  protected val dataViewMapping: Mapping[DataView] = mapping(
    "tableColumnNames" -> of[Seq[String]],
    "distributionCalcFieldNames" -> of[Seq[String]],
    "elementGridWidth" -> number(min = 1, max = 12),
    "generationMethod" -> of[WidgetGenerationMethod.Value]
  )(DataView.applyMain) { (item: DataView) =>
    Some((
      item.tableColumnNames,
      item.widgetSpecs.collect { case p: DistributionWidgetSpec => p }.map(_.fieldName),
      item.elementGridWidth,
      item.generationMethod
    ))
  }

  protected val extraMappings: Traversable[(String, Mapping[_])] = Nil

  protected[controllers] lazy val form: Form[E] = Form(
    GenericMapping.applyCaseClass[E](
      typeOf[E],
      Seq(
        "_id" -> ignored(Option.empty[BSONObjectID]),
        "dataSpaceName" -> nonEmptyText,
        "dataSetId" -> dataSetIdMapping,
        "dataSetName" -> nonEmptyText,
        "scheduled" -> boolean,
        "scheduledTime" -> optional(scheduledTimeMapping),
        "setting" -> optional(dataSetSettingMapping),
        "dataView" -> optional(dataViewMapping),
        "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
        "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
      ) ++ extraMappings
    ).verifying(
      "Import is marked as 'scheduled' but no time provided",
      importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
    )
  )

  protected val viewElements: (Form[E], WebContext) => Html

  protected def editViews(form: Form[E])(implicit webContext: WebContext) =
    view.edit(form, className, imagePath, imageLink)(viewElements(form, webContext))(webContext.msg, webContext.configuration)

  protected val defaultCreateInstance: Option[() => E] = None

  override protected[controllers] def fillForm(item: E) =
    form.fill(item)

  override protected def createView = { implicit ctx: WebContext =>
    form: Form[E] =>
      val filledForm = if (form.hasErrors) form else defaultCreateInstance.map(x => form.fill(x())).getOrElse(form)

      layout.create(
        displayName + " " + humanReadableSuffix,
        messagePrefix,
        filledForm,
        editViews(filledForm),
        routes.DataSetImportController.save,
        routes.DataSetImportController.listAll(),
        None,
        Seq('enctype -> "multipart/form-data")
      )
  }

  override protected def editView = { implicit ctx: WebContext =>
    data: IdForm[BSONObjectID, E] =>
      layout.edit(
        displayName + " " + humanReadableSuffix,
        messagePrefix,
        data.form.errors,
        editViews(data.form),
        routes.DataSetImportController.update(data.id),
        routes.DataSetImportController.listAll(),
        Some(routes.DataSetImportController.delete(data.id)),
        formArgs = Seq('enctype -> "multipart/form-data")
      )
  }
}