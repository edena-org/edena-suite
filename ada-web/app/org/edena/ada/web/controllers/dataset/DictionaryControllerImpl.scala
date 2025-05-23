package org.edena.ada.web.controllers.dataset

import akka.actor.ActorSystem
import akka.stream.Materializer

import javax.inject.Inject
import org.edena.ada.server.models.{URLType, DistributionWidgetSpec, _}

import scala.reflect.runtime.universe.TypeTag
import com.google.inject.assistedinject.Assisted
import org.edena.ada.web.controllers._
import org.edena.ada.web.controllers.core.AdaCrudControllerImpl
import org.edena.ada.web.controllers.core.{ExportableAction, WidgetRepoController}
import org.edena.ada.web.models.Widget
import org.edena.ada.server.dataaccess.StoreTypes.CategoryStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.edena.ada.server.dataaccess.dataset.FieldStore._
import org.edena.core.store.Criterion._
import org.edena.core.FilterCondition
import org.edena.core.store.AscSort
import org.edena.play.Page
import org.edena.play.controllers.{CrudControllerImpl, HasFormShowEqualEditView, WebContext}
import org.edena.play.formatters.{EnumFormatter, MapJsonFormatter}
import play.api.data.Form
import play.api.data.Forms.{optional, _}
import play.api.mvc.{Action, ControllerComponents, Request}
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.services._
import org.edena.ada.server.services.StatsService
import org.edena.ada.server.field.FieldUtil
import org.edena.ada.server.models.ml.classification.ClassificationResult
import org.edena.ada.server.field.FieldUtil.caseClassToFlatFieldTypes
import org.edena.ada.web.services.{DataSpaceService, WidgetGenerationService}
import org.edena.core.field.FieldTypeId
import views.html.{dataview, dictionary => view}
import org.edena.core.util.toHumanReadableCamel

import org.edena.core.DefaultTypes.Seq
import scala.concurrent.Future

trait DictionaryControllerFactory {
  def apply(dataSetId: String): DictionaryController
}

protected[controllers] class DictionaryControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService,
    statsService: StatsService,
    dataSpaceService: DataSpaceService,
    val wgs: WidgetGenerationService,
    val controllerComponents: ControllerComponents)(
    implicit actorSystem: ActorSystem, materializer: Materializer
  ) extends AdaCrudControllerImpl[Field, String](dsaf.applySync(dataSetId).get.fieldStore)
    with DictionaryController
    with WidgetRepoController[Field]
    with HasFormShowEqualEditView[Field, String]
    with ExportableAction[Field] {

  protected val dsa: DataSetAccessor = dsaf.applySync(dataSetId).get
  protected val categoryRepo: CategoryStore = dsa.categoryStore

  override protected val listViewColumns = Some(Seq("name", "fieldType", "isArray", "label", "categoryId"))
  override protected val entityNameKey = "field"

  private val exportOrderByFieldName = "name"
  private val csvFileName = "dictionary_" + dataSetId.replace(" ", "-") + ".csv"
  private val jsonFileName = "dictionary_" + dataSetId.replace(" ", "-") + ".json"

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"

  implicit val fieldTypeFormatter = EnumFormatter(FieldTypeId)
  implicit val fieldUrlDisplayTypeFormatter = EnumFormatter(URLType)
  implicit val mapFormatter = MapJsonFormatter.apply

  private val fields = caseClassToFlatFieldTypes[Field]("-", Set("category"))
  private val allFieldNames = fields.map(_._1).toSeq

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  override protected val typeTag = implicitly[TypeTag[Field]]
  override protected val format = fieldFormat
  override protected val excludedFieldNames = Set("category", "enumValues")

  private val fieldNameLabels = Seq(
    ("fieldType", Some("Field Type")),
    ("isArray", Some("Is Array?")),
    ("label", Some("Label")),
    ("name", Some("Name"))
  )
  private val fieldNameLabelMap = fieldNameLabels.toMap

  override protected[controllers] val form = Form(
    mapping(
      "name" -> nonEmptyText,
      "label" ->  optional(nonEmptyText),
      "fieldType" -> of[FieldTypeId.Value],
      "isArray" -> boolean,
      "enumValues" -> of[Map[String, String]],
      "displayDecimalPlaces" ->  optional(number(0, 20)),
      "displayTrueValue" ->  optional(nonEmptyText),
      "displayFalseValue" ->  optional(nonEmptyText),
      "displayAsURLType" ->  optional(of[URLType.Value]),
      "aliases" ->  seq(nonEmptyText),
      "categoryId" -> optional(nonEmptyText)
      // TODO: make it prettier perhaps by moving the category stuff to proxy/subclass of Field
    ) { (name, label, fieldType, isArray, numValues, displayDecimalPlaces, displayTrueValue, displayFalseValue, displayAsURLType, aliases, categoryId) =>
      Field(name, label, fieldType, isArray, numValues, displayDecimalPlaces, displayTrueValue, displayFalseValue, displayAsURLType, aliases, categoryId.map(BSONObjectID.parse(_).get))
    }
    ((field: Field) => Some(
      field.name,
      field.label,
      field.fieldType,
      field.isArray,
      field.enumValues,
      field.displayDecimalPlaces,
      field.displayTrueValue,
      field.displayFalseValue,
      field.displayAsURLType,
      field.aliases,
      field.categoryId.map(_.stringify))
    )
  )

  protected val router = new DictionaryRouter(dataSetId)
  protected val jsRouter = new DictionaryJsRouter(dataSetId)

  override protected lazy val homeCall = router.plainList

  // create view and  data

  override protected type CreateViewData = (String, Form[Field], Traversable[Category])

  override protected def getFormCreateViewData(form: Form[Field]) = {
    val dataSetNameFuture = dsa.dataSetName
    val categoriesFuture = allCategoriesFuture

    for {
      // get the data set name
      dataSetName <- dataSetNameFuture

      // get all the categories
      allCategories <- categoriesFuture
    } yield
      (dataSetName + " Field", form, allCategories)
  }

  override protected def createView = { implicit ctx =>
    (view.create(_, _, _)).tupled
  }

  // edit view and data (= show view)

  override protected type EditViewData = (
    String,
    String,
    Form[Field],
    Traversable[Category],
    DataSetSetting,
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getFormEditViewData(
    id: String,
    form: Form[Field]
  ) = { request =>
    val dataSetNameFuture = dsa.dataSetName
    val categoriesFuture = allCategoriesFuture
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val settingFuture = dsa.setting

    for {
      // get the data set name
      dataSetName <- dataSetNameFuture

      // retrieve all the categories
      allCategories <- categoriesFuture

      // get the data space tree
      tree <- treeFuture

      // get the setting
      setting <- settingFuture
    } yield
      (dataSetName + " Field", id, form, allCategories, setting, tree)
  }

  override protected def editView = { implicit ctx =>
    (view.edit(_, _, _, _, _, _)).tupled
  }

  // list view and data

  override protected type ListViewData = (
    String,
    Page[Field],
    Seq[FilterCondition],
    Traversable[Widget],
    Traversable[(String, Option[String])],
    DataSetSetting,
    Traversable[DataSpaceMetaInfo]
  )

  private val widgetSpecs = Seq(
    DistributionWidgetSpec("fieldType", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Pie)))
  )

  override protected def getListViewData(
    page: Page[Field],
    conditions: Seq[FilterCondition]
  ) = { request =>
    val newConditions = conditions.map { condition =>
      val label = fieldNameLabelMap.get(condition.fieldName.trim)
      condition.copy(fieldLabel = label.flatten)
    }

    // create futures as vals so they are executed in parallel
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)

    val nameFuture = dsa.dataSetName

    val widgetsFuture = toCriterion(newConditions).flatMap(criterion =>
      widgets(widgetSpecs, criterion)
    )

    val setCategoriesFuture = setCategoriesById(categoryRepo, page.items)

    val settingFuture = dsa.setting

    for {
      // get the data space tree
      tree <- treeFuture

      // get the data set name
      dataSetName <- nameFuture

      // create widgets
      widgets <- widgetsFuture

      // set categories
      _ <- setCategoriesFuture

      // get the setting
      setting <- settingFuture
    } yield
      (dataSetName + " Field", page, newConditions, widgets.flatten, fieldNameLabels, setting, tree)
  }

  override protected def listView = { implicit ctx =>
    (view.list(_, _, _, _, _, _, _)).tupled
  }

  // actions

  override def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = {
    val eolToUse = eol match {
      case Some(eol) => if (eol.trim.nonEmpty) eol.trim else csvEOL
      case None => csvEOL
    }

    val fieldsNames = if (tableColumnsOnly) listViewColumns.get else allFieldNames

    exportToCsv(
      csvFileName,
      delimiter,
      eolToUse,
      if (replaceEolWithSpace) csvCharReplacements else Nil)(
      fieldsNames,
      Some(exportOrderByFieldName),
      filter,
      useProjection = tableColumnsOnly
    )
  }

  /**
    * Generate content of Json export file and create donwload.
    *
    * @return View for download.
    */
  override def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) =
    exportToJson(
      jsonFileName)(
      Some(exportOrderByFieldName),
      filter,
      None,
      if (tableColumnsOnly) listViewColumns.get else Nil
    )

  override def updateLabel(id: String, label: String) = AuthAction { implicit request =>
    store.get(id).flatMap(_.fold(
      Future(NotFound(s"$entityName '${formatId(id)}' not found"))
    ){ field =>
      updateCall(field.copy(label = Some(label))).map(_ => Ok("Done"))
    })
  }

  override def setDefaultLabels = Action.async { implicit request =>
    for {
      fields <- store.find()

      fieldsWithoutLabel = fields.filter(_.label.isEmpty).map(field =>
        field.copy(label = Some(field.name))
      )

      _ <- store.update(fieldsWithoutLabel)
    } yield
      goHome.flashing("success" -> s"Default labels successfully set for ${fieldsWithoutLabel.size} fields.")
  }

  override def convertLabelsToCamelCase = Action.async { implicit request =>
    for {
      fields <- store.find()

      fieldsWithNewLabel = fields.filter(_.label.nonEmpty).map(field =>
        field.copy(label = Some(toHumanReadableCamel(field.label.get)))
      )

      _ <- store.update(fieldsWithNewLabel)
    } yield
      goHome.flashing("success" -> s"Camel-case labels successfully set for ${fieldsWithNewLabel.size} fields.")
  }

  protected def allCategoriesFuture =
    categoryRepo.find(sort = Seq(AscSort("name")))

  override protected def filterValueConverters(
    fieldNames: Traversable[String]
  ) =
    FieldUtil.valueConverters(fieldCaseClassRepo, fieldNames)
}