package org.edena.ada.web.controllers.dataset

import java.util.UUID
import java.{util => ju}
import akka.actor.ActorSystem
import akka.stream.Materializer

import javax.inject.Inject
import org.edena.ada.web.util.WebExportUtil._
import org.edena.ada.web.util.shorten

import scala.collection.mutable.{Map => MMap}
import org.edena.json.util._
import org.edena.core.util.{GroupMapList, seqFutures}
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.edena.ada.server.models.{MultiChartDisplayOptions, _}
import org.edena.ada.server.field.FieldUtil.{FieldOps, valueConverters}
import com.google.inject.assistedinject.Assisted
import org.edena.ada.web.controllers.{routes, _}
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.apache.commons.lang3.StringEscapeUtils
import org.edena.ada.server.models.Filter.FilterOrId
import org.edena.json._
import org.edena.ada.server.models.ml._
import org.edena.ada.web.controllers.dataset.IndependenceTestResult._
import org.edena.ada.server.dataaccess.StoreTypes.{ClassifierStore, ClusteringStore, RegressorStore}
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.data.{Form, Mapping}
import play.api.data.Forms.{mapping, number, of, optional}
import play.api.libs.json._
import play.api.mvc.Results.{Redirect, _}
import play.api.data.Forms.{mapping, _}
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.services._
import org.edena.store.json.BSONObjectIDFormat
import org.edena.ada.server.services.ml.{MachineLearningService, TransformationService}
import views.html.dataset
import org.edena.ada.server.models.datatrans.DataSetTransformation2._
import org.edena.ada.server.services.StatsService
import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.edena.ada.server.AdaException
import org.edena.ada.server.field.{FieldType, FieldTypeHelper}
import org.edena.ada.web.controllers.FilterConditionExtraFormats.coreFilterConditionFormat
import org.edena.ada.web.controllers.core.{AdaExceptionHandler, AdaReadonlyControllerImpl, ExportableAction}
import org.edena.ada.web.models._
import org.edena.ada.server.models._
import org.edena.ada.server.models.Filter
import org.edena.ada.server.models.datatrans._
import org.edena.core.{ConditionType, FilterCondition}
import org.edena.core.ConditionType._
import org.edena.core.FilterCondition.toCriterion
import org.edena.core.store._
import org.edena.core.store.Criterion.Infix
import org.edena.play.{Page, PageOrder}
import org.edena.play.controllers._
import org.edena.play.formatters._
import org.edena.play.util.WebUtil.toSort
import org.edena.spark_ml.models.VectorScalerType
import org.edena.ada.server.services.importers.TranSMARTService
import org.edena.ada.web.services.{DataSpaceService, WidgetGenerationService}
import org.edena.ada.web.services.widgetgen.DistributionWidgetGeneratorHelper
import org.edena.core.calc.impl.{ChiSquareResult, IndependenceTestResult, OneWayAnovaResult, Quartiles}
import org.edena.core.store.ValueMapAux._
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.store.json.JsObjectIdentity
import play.api.mvc.{AnyContent, Call, ControllerComponents, Request, Result}

import scala.math.Ordering.Implicits._
import scala.concurrent.{Future, TimeoutException}

trait GenericDataSetControllerFactory {
  def apply(dataSetId: String): DataSetController
}

protected[controllers] class DataSetControllerImpl @Inject() (
  @Assisted val dataSetId: String,
  dsaf: DataSetAccessorFactory,
  mlService: MachineLearningService,
  statsService: StatsService,
  dataSetService: DataSetService,
  transformationService: TransformationService,
  widgetService: WidgetGenerationService,
  clusteringRepo: ClusteringStore,
  val dataSpaceService: DataSpaceService,
  tranSMARTService: TranSMARTService,
  val controllerComponents: ControllerComponents)(
    implicit actorSystem: ActorSystem, materializer: Materializer
  ) extends AdaReadonlyControllerImpl[JsObject, BSONObjectID]
    with DataSetController
    with ExportableAction[JsObject]
    with DistributionWidgetGeneratorHelper
    with DataSetViewHelper {

  protected val dsa: DataSetAccessor = dsaf.applySync(dataSetId).get

  protected val fieldRepo = dsa.fieldStore
  protected val categoryRepo = dsa.categoryStore
  protected val filterRepo = dsa.filterStore
  protected val dataViewRepo = dsa.dataViewStore

  private val logger = Logger // (this.getClass())

  // note that the associated data set repo could be updated (by calling updateDataSetRepo)
  // therefore it should not be stored as val
  override protected def store = dsa.dataSetStore

  // TODO: replace with a proper cache
  private val jsonWidgetResponseCache = MMap[String, Future[Seq[JsArray]]]()

  // auto-generated filename for csv files
  protected def csvFileName: String = dataSetId.replace(" ", "-") + ".csv"

  // auto-generated filename for json files
  protected def jsonFileName: String = dataSetId.replace(" ", "-") + ".json"

  // auto-generated filename for tranSMART data files
  protected def tranSMARTDataFileName: String = dataSetId.replace(" ", "-") + "_data_file"

  // auto-generated filename for tranSMART mapping files
  protected def tranSMARTMappingFileName: String = dataSetId.replace(" ", "-") + "_mapping_file"

  // router for requests; to be passed to views as helper.
  protected val router = new DataSetRouter(dataSetId)

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"
  private val numericTypes = Set(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date)

  private lazy val doubleFieldType = ftf(FieldTypeSpec(FieldTypeId.Double)).asValueOf[Double]

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  override protected def formatId(id: BSONObjectID) = id.stringify

  private implicit val storageTypeFormatter = EnumFormatter(StorageType)
  private implicit val seriesProcessingSpec = JsonFormatter[SeriesProcessingSpec]
  private implicit val seriesTransformationSpec = JsonFormatter[SeriesTransformationSpec]

  private val distributionGenMethod = WidgetGenerationMethod.RepoAndFullData
  private val cumulativeCountGenMethod = WidgetGenerationMethod.FullData
  private val scatterGenMethod = WidgetGenerationMethod.FullData
  private val correlationsGenMethod = WidgetGenerationMethod.StreamedAll
  private val comparisonGenMethod = WidgetGenerationMethod.RepoAndFullData
  private val xLineChartGenMethod = WidgetGenerationMethod.StreamedAll
  private val independenceTestKeepUndefined = false

  private val resultDataSetMapping: Mapping[ResultDataSetSpec] = mapping(
    "id" -> nonEmptyText,
    "name" -> nonEmptyText,
    "storageType" -> of[StorageType.Value]
  )(ResultDataSetSpec.apply)(ResultDataSetSpec.unapply)

  private val seriesProcessingSpecForm = Form(
    mapping(
      "sourceDataSetId" -> ignored(dataSetId),
      "resultDataSetSpec" -> resultDataSetMapping,
      "seriesProcessingSpecs" -> seq(of[SeriesProcessingSpec]),
      "preserveFieldNames" -> seq(nonEmptyText),
      "processingBatchSize" -> optional(number(min = 1, max = 200)),
      "saveBatchSize" -> optional(number(min = 1, max = 200))
    )(DataSetSeriesProcessingSpec.apply)(DataSetSeriesProcessingSpec.unapply))

  private val seriesTransformationSpecForm = Form(
    mapping(
      "sourceDataSetId" -> ignored(dataSetId),
      "resultDataSetSpec" -> resultDataSetMapping,
      "seriesTransformationSpecs" -> seq(of[SeriesTransformationSpec]),
      "preserveFieldNames" -> seq(nonEmptyText),
      "processingBatchSize" -> optional(number(min = 1, max = 200)),
      "saveBatchSize" -> optional(number(min = 1, max = 200))
    )(DataSetSeriesTransformationSpec.apply)(DataSetSeriesTransformationSpec.unapply))

  override protected def listViewColumns = result(
    dataViewRepo.find().map {
      _.filter(_.default).headOption.map(_.tableColumnNames)
    }
  )

  override protected lazy val homeCall = router.getDefaultView(Nil)

  // list view and data

  override protected type ListViewData = (
    String,
      Page[JsObject],
      Seq[FilterCondition],
      Map[String, String],
      Seq[String]
    )

  override protected def getListViewData(
    page: Page[JsObject],
    conditions: Seq[FilterCondition]
  ) = { request =>
    val dataSetNameFuture = dsa.dataSetName
    val fieldLabelMapFuture = getFieldLabelMap(listViewColumns.get)
    val settingFuture = dsa.setting

    for {
      dataSetName <- dataSetNameFuture
      fieldLabelMap <- fieldLabelMapFuture
      setting <- settingFuture
    } yield
      (itemName(dataSetName, setting), page, conditions, fieldLabelMap, listViewColumns.get)
  }

  private def itemName(
    dataSetName: String,
    setting: DataSetSetting
  ) =
    setting.displayItemName.getOrElse(dataSetName + " Item")

  override protected[controllers] def listView = { implicit ctx =>
    (dataset.list(_, _, _, _, _)).tupled
  }

  // show view and data

  override type ShowViewData = DataSetShowViewDataHolder

  override protected def getShowViewData(
    id: BSONObjectID,
    item: JsObject
  ) = { request =>
    val dataSetNameFuture = dsa.dataSetName
    val dataSpaceTreeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val fieldsFuture = fieldRepo.find()
    val settingFuture = dsa.setting

    for {
      // get the data set name
      dataSetName <- dataSetNameFuture

      // get the data space name
      dataSpaceTree <- dataSpaceTreeFuture

      // get all the fields
      fields <- fieldsFuture

      // setting
      setting <- settingFuture
    } yield {
      // create a field name label / renderer map
      val fieldNameLabelAndRendererMap = fields.map { field =>
        val fieldType = ftf(field.fieldTypeSpec)
        (field.name, (field.labelOrElseName, fieldType.jsonToDisplayString(_)))
      }.toMap

      DataSetShowViewDataHolder(
        id,
        itemName(dataSetName, setting),
        item,
        router.getDefaultView(Nil),
        true,
        fieldNameLabelAndRendererMap,
        setting,
        dataSpaceTree
      )
    }
  }

  override protected[controllers] def showView = { implicit ctx => dataset.show(_) }

  override protected def filterValueConverters(
    fieldNames: Traversable[String]
  ): Future[Map[String, String => Option[Any]]] =
    valueConverters(fieldRepo, fieldNames)

  /**
    * Generate content of csv export file and create download.
    *
    * @param delimiter Delimiter for csv output file.
    * @return View for download.
    */
  override def exportViewRecordsAsCsv(
    dataViewId: BSONObjectID,
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean,
    escapeStringValues: Boolean
  ) = Action.async { implicit request =>

    for {
      tableFieldNames <- if (tableColumnsOnly) dataViewTableColumnNames(dataViewId) else Future(Nil)

      result <- exportTableRecordsAsCsvAux(
        tableFieldNames, delimiter, replaceEolWithSpace, eol, filter, None, useDisplayValues, escapeStringValues
      )
    } yield
      result
  }

  override def exportTableRecordsAsCsv(
    tableColumnNames: Seq[String],
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean,
    escapeStringValues: Boolean,
    selectedOnly: Boolean,
    selectedIds: Seq[BSONObjectID]
  ) = Action.async { implicit request =>

    val exportFieldNames = if (tableColumnsOnly) tableColumnNames else Nil

    val extraCriterion = if (selectedOnly)
      Some(JsObjectIdentity.name #-> selectedIds)
    else
      None

    exportTableRecordsAsCsvAux(
      exportFieldNames, delimiter, replaceEolWithSpace, eol, filter, extraCriterion, useDisplayValues, escapeStringValues
    )
  }

  private def exportTableRecordsAsCsvAux(
    tableFieldNames: Seq[String],
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    extraCriterion: Option[Criterion] = None,
    useDisplayValues: Boolean = false,
    escapeStringValues: Boolean = false)(
    implicit request: Request[AnyContent]
    ): Future[Result] = {
    val eolToUse = eol match {
      case Some(eol) => if (eol.trim.nonEmpty) eol.trim else csvEOL
      case None => csvEOL
    }

    val replacements = if (replaceEolWithSpace) csvCharReplacements else Nil

    for {
      // get the setting
      setting <- dsa.setting

      // load the requested fields or all
      fields <- if (tableFieldNames.nonEmpty) getFields(tableFieldNames) else fieldRepo.find()

      // get the result by calling exportToCsv core function
      result <- {
        // sort field names if use ALL otherwise use the original order
        val headerFieldNames = if (tableFieldNames.nonEmpty) tableFieldNames else fields.map(_.name).toSeq.sorted

        val nameFieldTypeMap: Map[String, FieldType[_]] = if (useDisplayValues) fields.map(field => (field.name, ftf(field.fieldTypeSpec))).toMap else Map[String, FieldType[_]]()

        exportToCsv(
          csvFileName,
          delimiter,
          eolToUse,
          replacements,
          escapeStringValues)(
          headerFieldNames,
          setting.exportOrderByFieldName,
          filter,
          extraCriterion,
          tableFieldNames.nonEmpty,
          nameFieldTypeMap
        ).apply(request)
      }
    } yield
      result
  }

  /**
    * Generate content of Json export file and create download.
    *
    * @return View for download.
    */
  override def exportViewRecordsAsJson(
    dataViewId: BSONObjectID,
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean
  ) = Action.async { implicit request =>
    for {
      tableFieldNames <- if (tableColumnsOnly) dataViewTableColumnNames(dataViewId) else Future(Nil)

      result <- exportTableRecordsAsJsonAux(
        tableFieldNames, filter, None, useDisplayValues
      )
    } yield
      result
  }

  override def exportTableRecordsAsJson(
    tableColumnNames: Seq[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean,
    useDisplayValues: Boolean,
    selectedOnly: Boolean,
    selectedIds: Seq[BSONObjectID]
  ) = Action.async { implicit request =>

    val extraCriterion = if (selectedOnly)
      Some(JsObjectIdentity.name #-> selectedIds)
    else
      None

    val exportFieldNames = if (tableColumnsOnly) tableColumnNames else Nil

    exportTableRecordsAsJsonAux(
      exportFieldNames, filter, extraCriterion, useDisplayValues
    )
  }

  private def exportTableRecordsAsJsonAux(
    tableFieldNames: Seq[String],
    filter: Seq[FilterCondition],
    extraCriterion: Option[Criterion] = None,
    useDisplayValues: Boolean = false
  )
    (
      implicit request: Request[AnyContent]
    ): Future[Result] = {
    for {
      // get the setting
      setting <- dsa.setting

      // load the requested fields or all
      fields <- if (tableFieldNames.nonEmpty) getFields(tableFieldNames) else fieldRepo.find()

      // get the result by calling exportToCsv core function
      result <- {
        // sort field names if use ALL otherwise use the original order
        val headerFieldNames = if (tableFieldNames.nonEmpty) tableFieldNames else fields.map(_.name).toSeq.sorted

        // name -> field type map
        val nameFieldTypeMap: Map[String, FieldType[_]] = if (useDisplayValues) fields.map(field => (field.name, ftf(field.fieldTypeSpec))).toMap else Map[String, FieldType[_]]()

        exportToJson(
          jsonFileName)(
          setting.exportOrderByFieldName,
          filter,
          extraCriterion,
          headerFieldNames,
          tableFieldNames.nonEmpty,
          nameFieldTypeMap
        ).apply(request)
      }
    } yield
      result
  }

  private def dataViewTableColumnNames(
    dataViewId: BSONObjectID
  ): Future[Seq[String]] =
    dataViewRepo.get(dataViewId).map {
      _ match {
        case Some(dataView) => dataView.tableColumnNames
        case None => Nil
      }
    }

  override def getDefaultView(
    filterOrIds: Seq[FilterOrId]
  ) = Action.async { implicit request =>
    for {
      //      // get the default view
      //      defaultView <- dataViewRepo.find(criteria = Seq("default" #== "true"), limit = Some(1)).map(_.headOption)
      //
      //      // if not available pick any view
      //      selectedView <- defaultView match {
      //        case Some(view) => Future(Some(view))
      //        case None => dataViewRepo.find(limit = Some(1)).map(_.headOption)
      //      }
      selectedView <- dataViewRepo.find(sort = Seq(DescSort("default")), limit = Some(1)).map(
        _.headOption
      )
    } yield
      selectedView match {
        case Some(view) => Redirect(router.getView(view._id.get, Nil, filterOrIds, false))
        case None => Redirect(new DataViewRouter(dataSetId).plainList).flashing("errors" -> "No view to show. You must first define one.")
      }
  }

  override def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[PageOrder],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ) = AuthAction { implicit request =>
    val start = new ju.Date()

    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val dataViewFuture = dataViewRepo.get(dataViewId)
    val settingFuture = dsa.setting

    {
      for {
        // get the data set name
        dataSetName <- dataSetNameFuture

        // get the data space tree
        dataSpaceTree <- treeFuture

        // load the view
        dataView <- dataViewFuture

        // setting
        setting <- settingFuture

        // check if the user can edit the view
        editPossible <- dataView.map(canEditView(_)).getOrElse(Future(false))

        // initialize filters
        filterOrIdsToUse: Seq[FilterOrId] =
          if (!filterChanged) {
            dataView.map(_.filterOrIds) match {
              case Some(viewFilterOrIds) =>
                val initViewFilterOrIds = if (viewFilterOrIds.nonEmpty) viewFilterOrIds else Seq(Left(Nil))

                val padding = Seq.fill(Math.max(initViewFilterOrIds.size - filterOrIds.size, 0))(Left(Nil))

                (filterOrIds ++ padding).zip(initViewFilterOrIds).map { case (filterOrId, viewFilterOrId) =>
                  if (filterOrId.isLeft && filterOrId.left.get.isEmpty) {
                    viewFilterOrId
                  } else
                    filterOrId
                }

              case None =>
                filterOrIds
            }
          } else
            filterOrIds

        // initialize table pages
        tablePagesToUse = {
          val padding = Seq.fill(Math.max(filterOrIdsToUse.size - tablePages.size, 0))(PageOrder(0, ""))
          tablePages ++ padding
        }

        // table column names and widget specs
        (tableColumnNames, widgetSpecs) = dataView.map(view => (view.tableColumnNames, view.widgetSpecs)).getOrElse((Nil, Nil))

        // load all the filters if needed
        resolvedFilters <- Future.sequence(filterOrIdsToUse.map(filterRepo.resolve))

        // collect the filters' conditions
        conditions = resolvedFilters.map(_.conditions)

        // convert the conditions to criteria
        criteria <- Future.sequence(conditions.map(toCriterion))

        // create a name -> field map of all the referenced fields for a quick lookup
        nameFieldMap <- createNameFieldMap(fieldRepo)(conditions, widgetSpecs, tableColumnNames)

        // default order by
        defaultOrderBy = dataView.flatMap(view => view.initTableSortFieldName.map(fieldName =>
          if (view.initTableSortType.isDefined && view.initTableSortType.get == SortType.Desc) s"-$fieldName" else fieldName
        )).getOrElse("")

        // get the response data
        viewResponses <-
          Future.sequence(
            (tablePagesToUse, criteria, resolvedFilters).zipped.map { case (tablePage, criteria, resolvedFilter) =>
              val orderBy = if (tablePage.orderBy.isEmpty) defaultOrderBy else tablePage.orderBy
              getInitViewResponse(store)(tablePage.page, orderBy, resolvedFilter, criteria, nameFieldMap, tableColumnNames, pageLimit)
            }
          )
      } yield {
        Logger.info(s"Data loading of a view for the data set '${dataSetId}' finished in ${new ju.Date().getTime - start.getTime} ms")
        val renderingStart = new ju.Date()
        render {
          case Accepts.Html() => {

            // init view parts - table view data
            val viewPartsWidgetFutures = (viewResponses, tablePagesToUse, criteria).zipped.map {
              case (viewResponse, tablePage, criteria) =>
                val newPage = Page(viewResponse.tableItems, tablePage.page, tablePage.page * pageLimit, viewResponse.count, tablePage.orderBy)
                val viewData = TableViewData(newPage, Some(viewResponse.filter), viewResponse.tableFields)
                val method = dataView.map(_.generationMethod).getOrElse(WidgetGenerationMethod.Auto)
                val widgets = getViewWidgets(widgetSpecs, criteria, nameFieldMap.values, method)

                (viewData, widgets)
            }

            val viewParts = viewPartsWidgetFutures.map(_._1)

            // widgets future
            val jsonsFuture = Future.sequence(viewPartsWidgetFutures.map(_._2)).map { allWidgets =>
              val minMaxWidgets = if (allWidgets.size > 1) setBoxPlotMinMax(allWidgets) else allWidgets

              minMaxWidgets.map(_.flatten).map(widgets => widgetsToJsons(widgets.toSeq))
            }

            val callbackId = UUID.randomUUID.toString
            jsonWidgetResponseCache.put(callbackId, jsonsFuture)

            val response = Ok(
              dataset.view.showView(
                dataSetName,
                itemName(dataSetName, setting),
                dataViewId,
                dataView.map(_.name).getOrElse("N/A"),
                viewParts,
                callbackId,
                dataView.map(_.elementGridWidth).getOrElse(3),
                setting,
                editPossible,
                dataSpaceTree
              )
            )
            Logger.info(s"Rendering of a view for the data set '${dataSetId}' finished in ${new ju.Date().getTime - renderingStart.getTime} ms")
            response
          }

          case Accepts.Json() => Ok(Json.toJson(viewResponses.head.tableItems))
        }
      }
    }.recover(handleExceptionsWithId("a show-view", dataViewId))
  }

  // This is just to demonstrate how an action view can be used... can be deleted
  private def getViewWithAction(
    dataViewId: BSONObjectID
  ) = AuthAction { implicit request =>
    val dataSetNameTreeSettingFuture = getDataSetNameTreeAndSetting(dsa)
    val dataViewFuture = dataViewRepo.get(dataViewId)

    for {
      // get the essentials - data set name + tree + setting
      (dataSetName, dataSpaceTree, setting) <- dataSetNameTreeSettingFuture

      // load the view
      dataView <- dataViewFuture

      _ = require(dataView.isDefined, s"Data view '${dataViewId.stringify}' not defined")

      // load all the filters if needed
      resolvedFilters <- Future.sequence(dataView.get.filterOrIds.map(filterRepo.resolve))

      // collect the filters' conditions
      conditions = resolvedFilters.map(_.conditions)

      // convert the conditions to criteria
      criteria <- Future.sequence(conditions.map(toCriterion))

      // table column names and widget specs
      tableColumnNames = dataView.get.tableColumnNames

        // initialize table pages
      tablePagesToUse = Seq.fill(dataView.get.filterOrIds.size)(PageOrder(0, ""))

      // create a name -> field map of all the referenced fields for a quick lookup
      nameFieldMap <- createNameFieldMap(fieldRepo)(conditions, Nil, tableColumnNames)

      // get the response data
      viewResponses <-
        Future.sequence(
          (tablePagesToUse, criteria, resolvedFilters).zipped.map { case (tablePage, criteria, resolvedFilter) =>
            getInitViewResponse(store)(tablePage.page, tablePage.orderBy, resolvedFilter, criteria, nameFieldMap, tableColumnNames, pageLimit)
          }
        )
    } yield {
      // init view parts - table view data
      val tableViewData = (viewResponses, tablePagesToUse).zipped.map {
        case (viewResponse, tablePage) =>
          val newPage = Page(viewResponse.tableItems, tablePage.page, tablePage.page * pageLimit, viewResponse.count, tablePage.orderBy)
          TableViewData(newPage, Some(viewResponse.filter), viewResponse.tableFields)
      }

      Ok(
        dataset.view.actionView(
          router.allFieldNamesAndLabels,
          "Export",
          "My Action View",
          itemName(dataSetName, setting),
          dataViewId,
          tableViewData,
          dataView.get.elementGridWidth,
          setting,
          dataSpaceTree
        )
      )
    }
  }

  private def widgetsToJsons(widgets: Seq[Widget]): JsArray = {
    import Widget.writes

    val jsons = widgets.map(Json.toJson(_))

    JsArray(jsons)
  }

  private def canEditView(
    dataView: DataView
  )
    (
      implicit request: AuthenticatedRequest[_]
    ): Future[Boolean] = for {
    user <- currentUser()
  } yield
    user.map { user =>
      val isOwner =
        dataView.createdById match {
          case Some(createdById) => user.id.get.equals(createdById)
          case None => false
        }

      user.isAdmin || isOwner
    }.getOrElse(
      false
    )

  override def getWidgets = Action.async { implicit request =>
    val callbackId = request.body.asFormUrlEncoded.flatMap(_.get("callbackId").map(_.head))

    callbackId match {
      case None => Future(BadRequest("No callback id provided."))
      case Some(callbackId) =>
        jsonWidgetResponseCache.get(callbackId).map { jsonWidgets =>
          jsonWidgetResponseCache.remove(callbackId)
          jsonWidgets.map(jsons => Ok(JsArray(jsons)))
        }.getOrElse(
          Future(NotFound(s"Widget response callback $callbackId not found."))
        )
    }
  }

  override def getViewElementsAndWidgetsCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    filterOrId: FilterOrId,
    oldCountDiff: Option[Int],
    tableSelection: Boolean
  ) = AuthAction { implicit request =>
    val tablePage = 0

    val dataSetNameFuture = dsa.dataSetName
    val dataViewFuture = dataViewRepo.get(dataViewId)
    val resolvedFilterFuture = filterRepo.resolve(filterOrId)
    val settingFuture = dsa.setting

    {
      for {
        // data set name
        dataSetName <- dataSetNameFuture

        // load the view
        dataView <- dataViewFuture

        // resolved filter
        resolvedFilter <- resolvedFilterFuture

        // setting
        setting <- settingFuture

        // criteria
        criteria <- toCriterion(resolvedFilter.conditions)

        (tableColumnNames, widgetSpecs) = dataView.map(view => (view.tableColumnNames, view.widgetSpecs)).getOrElse((Nil, Nil))

        // create a name -> field map of all the referenced fields for a quick lookup
        nameFieldMap <- createNameFieldMap(fieldRepo)(Seq(resolvedFilter.conditions), widgetSpecs, tableColumnNames)

        // get the init response data
        viewResponse <- getInitViewResponse(store)(tablePage, tableOrder, resolvedFilter, criteria, nameFieldMap, tableColumnNames, pageLimit)
      } yield {
        //        Logger.info(s"Data loading of a widget panel and a table for the data set '${dataSetId}' finished in ${new ju.Date().getTime - start.getTime} ms")

        // create a widgets-future calculation and register with a callback id
        val method = dataView.map(_.generationMethod).getOrElse(WidgetGenerationMethod.Auto)
        val jsonWidgetsFuture = getViewWidgets(widgetSpecs, criteria, nameFieldMap.values, method).map { widgets =>
          val jsons = widgetsToJsons(widgets.flatten.toSeq)
          Seq(jsons)
        }
        val widgetsCallbackId = UUID.randomUUID.toString
        jsonWidgetResponseCache.put(widgetsCallbackId, jsonWidgetsFuture)

        val newPage = Page(viewResponse.tableItems, tablePage, tablePage * pageLimit, viewResponse.count, tableOrder)

        val pageHeader = messagesApi.apply("list.count.title", oldCountDiff.getOrElse(0) + viewResponse.count, itemName(dataSetName, setting))

        val table = dataset.dataSetTable(newPage, Some(viewResponse.filter), viewResponse.tableFields, true, tableSelection)
        val conditionPanel = views.html.filter.conditionPanel(Some(viewResponse.filter))
        val filterModel = Json.toJson(viewResponse.filter.conditions)

        val jsonResponse = Ok(Json.obj(
          "table" -> table.toString(),
          "conditionPanel" -> conditionPanel.toString(),
          "filterModel" -> filterModel,
          "count" -> viewResponse.count,
          "pageHeader" -> pageHeader,
          "widgetsCallbackId" -> widgetsCallbackId
        ))

        render {
          case Accepts.Html() => jsonResponse
          case Accepts.Json() => jsonResponse
        }
      }
    }.recover(handleExceptionsWithErrorCodes("a getViewElementsAndWidgetsCallback"))
  }

  override def getNewFilterViewElementsAndWidgetsCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    totalCount: Int
  ) = AuthAction { implicit request =>
    val tablePage = 0

    val dataSetNameFuture = dsa.dataSetName
    val dataViewFuture = dataViewRepo.get(dataViewId)
    val settingFuture = dsa.setting
    val noOpCriterion = NoCriterion

    {
      for {
        // data set name
        dataSetName <- dataSetNameFuture

        // load the view
        dataView <- dataViewFuture

        // setting
        setting <- settingFuture

        (tableColumnNames, widgetSpecs) = dataView.map(view => (view.tableColumnNames, view.widgetSpecs)).getOrElse((Nil, Nil))

        // create a name -> field map of all the referenced fields for a quick lookup
        nameFieldMap <- createNameFieldMap(fieldRepo)(Nil, widgetSpecs, tableColumnNames)

        // get the init response data
        viewResponse <- getInitViewResponse(store)(tablePage, tableOrder, new Filter(), noOpCriterion, nameFieldMap, tableColumnNames, pageLimit)
      } yield {
        // create a widgets-future calculation and register with a callback id
        val method = dataView.map(_.generationMethod).getOrElse(WidgetGenerationMethod.Auto)
        val jsonWidgetsFuture = getViewWidgets(widgetSpecs, noOpCriterion, nameFieldMap.values, method).map { widgets =>
          val jsons = widgetsToJsons(widgets.flatten.toSeq)
          Seq(jsons)
        }
        val widgetsCallbackId = UUID.randomUUID.toString
        jsonWidgetResponseCache.put(widgetsCallbackId, jsonWidgetsFuture)

        val newPage = Page(viewResponse.tableItems, tablePage, tablePage * pageLimit, viewResponse.count, tableOrder)

        val pageHeader = messagesApi.apply("list.count.title", totalCount + viewResponse.count, itemName(dataSetName, setting))

        val table = dataset.dataSetTable(newPage, None, viewResponse.tableFields, true)
        val countFilter = dataset.view.viewCountFilter(None, viewResponse.count, setting.filterShowFieldStyle, false)

        val jsonResponse = Ok(Json.obj(
          "table" -> table.toString(),
          "countFilter" -> countFilter.toString(),
          "pageHeader" -> pageHeader,
          "widgetsCallbackId" -> widgetsCallbackId
        ))

        render {
          case Accepts.Html() => jsonResponse
          case Accepts.Json() => jsonResponse
        }
      }
    }.recover(handleExceptionsWithErrorCodes("a getNewFilterViewElementsAndWidgetsCallback"))
  }

  override def getNewFilter = AuthAction { implicit request => {
      dsa.setting.map { setting =>
        val filter = dataset.filter.dataSetFilter(None, setting.filterShowFieldStyle)
        Ok(filter.toString())
      }
    }.recover(handleExceptionsWithErrorCodes("a getNewFilter"))
  }

  private def getFilterAndWidgetsCallbackAux(
    widgetSpecs: Seq[WidgetSpec],
    generationMethod: WidgetGenerationMethod.Value,
    filterOrId: FilterOrId,
    widgetProcessingFun: Option[Traversable[Widget] => Traversable[Widget]] = None)(
    implicit request: Request[_]
  ): Future[Result] =
    for {
      // resolved filter
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // criteria
      criteria <- toCriterion(resolvedFilter.conditions)

      // create a name -> field map of all the referenced fields for a quick lookup
      nameFieldMap <- createNameFieldMap(fieldRepo)(Seq(resolvedFilter.conditions), widgetSpecs, Nil)
    } yield {
      // create a widgets-future calculation and register with a callback id
      val jsonWidgetsFuture = getViewWidgets(widgetSpecs, criteria, nameFieldMap.values, generationMethod).map { widgets =>
        val processedWidgets = widgetProcessingFun.map(_(widgets.flatten)).getOrElse(widgets.flatten)
        val jsons = widgetsToJsons(processedWidgets.toSeq)
        Seq(jsons)
      }
      val widgetsCallbackId = UUID.randomUUID.toString
      jsonWidgetResponseCache.put(widgetsCallbackId, jsonWidgetsFuture)

      // get a new filter
      val newFilter = setFilterLabels(resolvedFilter, nameFieldMap)
      val conditionPanel = views.html.filter.conditionPanel(Some(newFilter))
      val filterModel = Json.toJson(newFilter.conditions)

      val jsonResponse = Ok(Json.obj(
        "conditionPanel" -> conditionPanel.toString(),
        "filterModel" -> filterModel,
        "widgetsCallbackId" -> widgetsCallbackId
      ))

      render {
        case Accepts.Html() => jsonResponse
        case Accepts.Json() => jsonResponse
      }
    }

  private def getViewWidgets(
    widgetSpecs: Seq[WidgetSpec],
    criterion: Criterion,
    fields: Traversable[Field],
    generationMethod: WidgetGenerationMethod.Value
  ): Future[Traversable[Option[Widget]]] = {
    // future to load sub-criteria
    val filterSubCriteriaFuture = filterSubCriteriaMap(widgetSpecs)

    for {
      // get the widgets' sub criteria
      filterSubCriteria <- filterSubCriteriaFuture

      widgetsWithFields <- widgetService(widgetSpecs, store, criterion, filterSubCriteria.toMap, fields, generationMethod)
    } yield
      widgetsWithFields
  }

  override def findCustom(
    filterOrId: FilterOrId,
    orderBy: String,
    projection: Seq[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = Action.async { implicit request =>
    for {
      // use a given filter conditions or load one
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // get the conditions
      conditions = resolvedFilter.conditions

      // generate criteria
      criteria <- toCriterion(conditions)

      // get the items
      items <- store.find(criteria, toSort(orderBy), projection, limit, skip)
    } yield
      render {
        case Accepts.Html() => BadRequest("Html version of the service is not available.")
        case Accepts.Json() => Ok(Json.toJson(items))
      }
  }

  private def getFilterFieldNameMap(
    filter: Filter)
  : Future[Map[String, Field]] =
    getFields(filter.conditions.map(_.fieldName)).map {
      _.map(field => (field.name, field)).toMap
    }

  private def getFields(
    fieldNames: Traversable[String]
  ): Future[Traversable[Field]] =
    if (fieldNames.isEmpty)
      Future(Nil)
    else
      fieldRepo.find(FieldIdentity.name #-> fieldNames.toSet.toSeq)

  override def getDistribution(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
      for {
        // get the filter (with labels), data set name, the data space tree, and the setting
        (filter, dataSetName, dataSpaceTree, setting) <- getFilterAndEssentials(filterOrId)

        // the default field
        field <- setting.defaultDistributionFieldName.map(fieldRepo.get).getOrElse(Future(None))
      } yield

        render {
          case Accepts.Html() => Ok(dataset.distribution(
            dataSetName,
            filter,
            setting,
            field,
            None,
            dataSpaceTree
          ))
          case Accepts.Json() => BadRequest("The function getDistribution doesn't support JSON response.")
        }

    }.recover(handleExceptions("a distribution"))
  }

  override def calcDistribution(
    fieldName: String,
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    {
      for {
        // the (main) field
        field <- fieldRepo.get(fieldName)

        // the group field
        groupField <- groupFieldName.map(fieldRepo.get).getOrElse(Future(None))

        // generate the required widget(s)
        response <- field match {
          case Some(field) =>

            def distWidgetSpec(chartType: ChartType.Value) =
              DistributionWidgetSpec(
                fieldName = field.name,
                groupFieldName = groupField.map(_.name),
                displayOptions = MultiChartDisplayOptions(
                  gridWidth = Some(6),
                  height = Some(500),
                  chartType = Some(chartType)
                )
              )

            val widgetSpecs =
              if (field.isNumeric) {
                val charType = if (groupField.isDefined) ChartType.Spline else ChartType.Column

                Seq(
                  distWidgetSpec(charType),
                  BoxWidgetSpec(
                    fieldName = field.name,
                    groupFieldName = groupField.map(_.name),
                    displayOptions = BasicDisplayOptions(gridWidth = Some(3), height = Some(500))
                  ),
                  BasicStatsWidgetSpec(
                    fieldName = field.name,
                    displayOptions = BasicDisplayOptions(gridWidth = Some(3), height = Some(500))
                  )
                )

              } else {
                val chartType = ChartType.Column // if (groupField.isDefined) ChartType.Column else ChartType.Pie
                val distributionWidget = distWidgetSpec(chartType)

                Seq(
                  distributionWidget,
                  distributionWidget.copy(displayOptions = distributionWidget.displayOptions.copy(isTextualForm = true))
                )
              }

            val genMethod =
              if (field.isString || groupField.map(_.isString).getOrElse(false))
                WidgetGenerationMethod.FullData
              else
                distributionGenMethod

            getFilterAndWidgetsCallbackAux(widgetSpecs, genMethod, filterOrId)

          case None =>
            Future(BadRequest(s"Field '$fieldName' not found."))
        }
      } yield
        response
    }.recover(handleExceptionsWithErrorCodes("a distribution"))
  }

  override def getCumulativeCount(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
    for {
        // get the filter (with labels), data set name, the data space tree, and the setting
        (filter, dataSetName, dataSpaceTree, setting) <- getFilterAndEssentials(filterOrId)

        // default field
        field <- setting.defaultCumulativeCountFieldName.map(fieldRepo.get).getOrElse(Future(None))
      } yield

        render {
          case Accepts.Html() => Ok(dataset.cumulativeCount(
            dataSetName,
            filter,
            setting,
            field,
            None,
            dataSpaceTree
          ))
          case Accepts.Json() => BadRequest("The function getCumulativeCount doesn't support JSON response.")
        }

    }.recover(handleExceptions("a cumulative count"))
  }

  override def calcCumulativeCount(
    fieldName: String,
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    {
      for {
        // the (main) field
        field <- fieldRepo.get(fieldName)

        // the group field
        groupField <- groupFieldName.map(fieldRepo.get).getOrElse(Future(None))

        // generate the required widget(s)
        response <- field match {
          case Some(field) =>

            val charType = if (groupField.isDefined) ChartType.Spline else ChartType.Column

            val widgetSpecs = Seq(
              CumulativeCountWidgetSpec(
                fieldName = field.name,
                groupFieldName = groupFieldName,
                displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Line), gridWidth = Some(6), height = Some(500))
              ),
              CumulativeCountWidgetSpec(
                fieldName = field.name,
                groupFieldName = groupFieldName,
                numericBinCount = Some(30),
                displayOptions = MultiChartDisplayOptions(chartType = Some(charType), gridWidth = Some(6), height = Some(500))
              )
            )

            val genMethod =
              if (groupField.map(_.isString).getOrElse(false))
                WidgetGenerationMethod.FullData
              else
                cumulativeCountGenMethod

            getFilterAndWidgetsCallbackAux(widgetSpecs, genMethod, filterOrId)

          case None =>
            Future(BadRequest(s"Field $fieldName not found."))
        }
      } yield
        response
    }.recover(handleExceptionsWithErrorCodes("a cumulative count"))
  }

  override def getScatter(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
      for {
        // get the filter (with labels), data set name, the data space tree, and the setting
        (filter, dataSetName, dataSpaceTree, setting) <- getFilterAndEssentials(filterOrId)

        // default x field
        xField <- setting.defaultScatterXFieldName.map(fieldRepo.get).getOrElse(Future(None))

        // default y field
        yField <- setting.defaultScatterYFieldName.map(fieldRepo.get).getOrElse(Future(None))
      } yield

        render {
          case Accepts.Html() => Ok(dataset.scatter(
            dataSetName,
            filter,
            setting,
            xField,
            yField,
            None,
            dataSpaceTree
          ))
          case Accepts.Json() => BadRequest("The function getScatter doesn't support JSON response.")
        }

    }.recover(handleExceptions("a scatter"))
  }

  override def calcScatter(
    xFieldName: String,
    yFieldName: String,
    groupOrValueFieldName: Option[String],
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    {
      for {
        // the x field
        xField <- fieldRepo.get(xFieldName)

        // the x field
        yField <- fieldRepo.get(yFieldName)

        // the group or  value field
        groupOrValueField <- groupOrValueFieldName.map(fieldRepo.get).getOrElse(Future(None))

        // generate the required widget(s)
        response <- (xField zip yField).headOption match {
          case Some((xField, yField)) =>

            val widgetSpecs = Seq(
              if (groupOrValueField.map(_.isNumeric).getOrElse(false))
                ValueScatterWidgetSpec(
                  xFieldName = xField.name,
                  yFieldName = yField.name,
                  valueFieldName = groupOrValueFieldName.get,
                  displayOptions = BasicDisplayOptions(height = Some(500))
                )
              else
                ScatterWidgetSpec(
                  xFieldName = xField.name,
                  yFieldName = yField.name,
                  groupFieldName  = groupOrValueFieldName,
                  displayOptions = BasicDisplayOptions(height = Some(500))
                )
            )

            getFilterAndWidgetsCallbackAux(widgetSpecs, scatterGenMethod, filterOrId)

          case None =>
            Future(BadRequest(s"Field $xFieldName or $yFieldName not found."))
        }
      } yield
        response
    }.recover(handleExceptionsWithErrorCodes("a scatter"))
  }

  override def getPearsonCorrelations(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
    for {
      // get the filter (with labels), data set name, the data space tree, and the setting
      (filter, dataSetName, dataSpaceTree, setting) <- getFilterAndEssentials(filterOrId)
    } yield

      render {
        case Accepts.Html() => Ok(dataset.pearsonCorrelation(
          dataSetName,
          filter,
          setting,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("The function getPearsonCorrelations function doesn't support JSON response.")
      }

    }.recover(handleExceptions("a Pearson correlation"))
  }

  override def calcPearsonCorrelations(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    val fieldNames = request.body.asFormUrlEncoded.flatMap(_.get("fieldNames[]")).getOrElse(Nil)

    {
      for {
        // the correlation fields
        fields <- getFields(fieldNames)

        // generate the required widget(s)
        response <- fields match {
          case Nil => Future(BadRequest(s"No fields provided."))

          case _ =>
            val widgetSpecs = Seq(
              CorrelationWidgetSpec(
                fieldNames,
                CorrelationType.Pearson,
                None,
                BasicDisplayOptions(height = Some(Math.max(550, fields.size * 20)))
              )
            )

            getFilterAndWidgetsCallbackAux(
              widgetSpecs,
              correlationsGenMethod,
              filterOrId,
              Some(processCorrelationsWidgets(fields.size))
            )
        }
      } yield
        response
    }.recover(handleExceptionsWithErrorCodes("Pearson correlations"))
  }

  private def processCorrelationsWidgets(fieldsCount: Int) = { widgets: Traversable[Widget] =>
    // if we have more than 50 fields for performance purposes we round correlations to 3 decimal places
    if (fieldsCount > 50) {
      widgets.map {widget =>
        if (widgets.isInstanceOf[HeatmapWidget]) {
          val heatmapWidget = widget.asInstanceOf[HeatmapWidget]
          val newData = heatmapWidget.data.map(_.map(_.map(value => Math.round(value * 1000).toDouble / 1000)))
          heatmapWidget.copy(data = newData)
        } else
          widget
      }
    } else
      widgets
  }

  override def getMatthewsCorrelations(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
    for {
      // get the filter (with labels), data set name, the data space tree, and the setting
      (filter, dataSetName, dataSpaceTree, setting) <- getFilterAndEssentials(filterOrId)
    } yield

      render {
        case Accepts.Html() => Ok(dataset.matthewsCorrelation(
          dataSetName,
          filter,
          setting,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("The function getMatthewsCorrelations function doesn't support JSON response.")
      }
    }.recover(handleExceptions("a Matthews correlation"))
  }

  override def calcMatthewsCorrelations(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    val fieldNames = request.body.asFormUrlEncoded.flatMap(_.get("fieldNames[]")).getOrElse(Nil)

    {
      for {
      // the correlation fields
        fields <- getFields(fieldNames)

        // generate the required widget(s)
        response <- fields match {
          case Nil =>
            Future(BadRequest(s"No fields provided."))

          case _ =>
            val widgetSpecs = Seq(
              CorrelationWidgetSpec(
                fieldNames,
                CorrelationType.Matthews,
                None,
                BasicDisplayOptions(height = Some(Math.max(550, fields.size * 20)))
              )
            )

            getFilterAndWidgetsCallbackAux(
              widgetSpecs,
              correlationsGenMethod,
              filterOrId,
              Some(processCorrelationsWidgets(fields.size))
            )
        }
      } yield
        response
    }.recover(handleExceptionsWithErrorCodes("Matthews correlations"))
  }

  override def getHeatmap(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
    for {
      // get the filter (with labels), data set name, the data space tree, and the setting
      (filter, dataSetName, dataSpaceTree, setting) <- getFilterAndEssentials(filterOrId)

//      // default x field
//      xField <- setting.defaultScatterXFieldName.map(fieldRepo.get).getOrElse(Future(None))
//
//      // default y field
//      yField <- setting.defaultScatterYFieldName.map(fieldRepo.get).getOrElse(Future(None))
    } yield

      render {
        case Accepts.Html() => Ok(dataset.heatmap(
          dataSetName,
          filter,
          setting,
          None,
          None,
          None,
          dataSpaceTree
        ))

        case Accepts.Json() => BadRequest("The function getHeatmap doesn't support JSON response.")
      }

    }.recover(handleExceptions("a chart"))
  }

  override def calcHeatmap(
    xFieldName: String,
    yFieldName: String,
    valueFieldName: Option[String],
    aggType: Option[AggType.Value],
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    {
      for {
        // the x field
        xField <- fieldRepo.get(xFieldName)

        // the x field
        yField <- fieldRepo.get(yFieldName)

        // the value field
        valueField <- valueFieldName.map(fieldRepo.get).getOrElse(Future(None))

        // generate the required widget(s)
        response <- (xField zip yField).headOption match {
          case Some((xField, yField)) =>

            val widgetSpecs = Seq(
              if (valueField.isDefined && aggType.isDefined)
                HeatmapAggWidgetSpec(
                  xFieldName = xField.name,
                  yFieldName = yField.name,
                  valueFieldName = valueField.get.name,
                  xBinCount = 20,
                  yBinCount = 20,
                  aggType = aggType.get,
                  displayOptions = BasicDisplayOptions(height = Some(500))
                )
              else
                GridDistributionCountWidgetSpec(
                  xFieldName = xField.name,
                  yFieldName = yField.name,
                  xBinCount = 20,
                  yBinCount = 20,
                  displayOptions = BasicDisplayOptions(height = Some(500))
                )
              )

            getFilterAndWidgetsCallbackAux(widgetSpecs, scatterGenMethod, filterOrId)

          case None =>
            Future(BadRequest(s"Field $xFieldName or $yFieldName not found."))
        }
      } yield
        response
    }.recover(handleExceptionsWithErrorCodes("a chart"))
  }

  override def getTable(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
    for {
      // get the filter (with labels), data set name, the data space tree, and the setting
      (filter, dataSetName, dataSpaceTree, setting) <- getFilterAndEssentials(filterOrId)
    } yield

      render {
        case Accepts.Html() => Ok(dataset.exportTable(
          dataSetName,
          filter,
          setting,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("The function getTable function doesn't support JSON response.")
      }

    }.recover(handleExceptionsWithErrorCodes("a export table"))
  }

  override def getComparison(
    filterOrIds: Seq[FilterOrId]
  ) = AuthAction { implicit request => {
    for {
      // get the data set name, the data space tree, and the setting
      (dataSetName, dataSpaceTree, setting) <- getDataSetNameTreeAndSetting(dsa)

      // load all the filters if needed
      resolvedFilters <- Future.sequence(filterOrIds.map(filterRepo.resolve))

      // create a name -> field map of the filter referenced fields and set the filter's fields' labels
      newFilters <- Future.sequence(resolvedFilters.map(resolvedFilter =>
        getFilterFieldNameMap(resolvedFilter).map(
          setFilterLabels(resolvedFilter, _)
        )
      ))
    } yield {
      val paddedFilters = if (newFilters.size < 2) Seq.fill(2 - newFilters.size)(new Filter()) else newFilters

      render {
        case Accepts.Html() => Ok(dataset.comparison(
          dataSetName,
          paddedFilters,
          setting,
          None,
          None,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("The function getComparison doesn't support JSON response.")
      }
    }

  }.recover(handleExceptions("a comparison"))
  }

  override def calcComparison(
    fieldName: String,
    filterOrIds: Seq[FilterOrId]
  ) = AuthAction { implicit request =>
    {
      for {
        // load all the filters if needed
        resolvedFilters <- Future.sequence(filterOrIds.map(filterRepo.resolve))

        newFilters <- Future.sequence(resolvedFilters.map(resolvedFilter =>
          createNameFieldMap(fieldRepo)(Seq(resolvedFilter.conditions), Nil, Nil).map( nameFieldMap =>
            setFilterLabels(resolvedFilter, nameFieldMap)
          )
        ))

        // the (main) field
        field <- fieldRepo.get(fieldName)
      } yield
        field match {
          case Some(field) =>
            val jsonWidgetsFuture = if (field.isNumeric) numericComparisonWidgetsAsJson(resolvedFilters, field) else categoricalComparisonWidgetsAsJson(resolvedFilters, field)

            val widgetsCallbackId = UUID.randomUUID.toString
            jsonWidgetResponseCache.put(widgetsCallbackId, jsonWidgetsFuture)

            // get new filters
            val conditionPanels = newFilters.zipWithIndex.map { case (newFilter, index) => views.html.filter.conditionPanel(Some(newFilter), s"Filter ${index + 1}").toString }
            val filterModels = newFilters.map(newFilter => Json.toJson(newFilter.conditions))

            val jsonResponse = Ok(Json.obj(
              "conditionPanels" -> conditionPanels,
              "filterModels" -> filterModels,
              "widgetsCallbackId" -> widgetsCallbackId
            ))

            render {
              case Accepts.Html() => jsonResponse
              case Accepts.Json() => jsonResponse
            }

          case None =>
            BadRequest(s"Field $fieldName not found.")
        }
    }.recover(handleExceptionsWithErrorCodes("a comparison"))
  }

  private def numericComparisonWidgetsAsJson(
    filters: Seq[Filter],
    field: Field
  ): Future[Seq[JsArray]] = {
    val distributionSpec = DistributionWidgetSpec(
      fieldName = field.name,
      groupFieldName = None,
      displayOptions = MultiChartDisplayOptions(
        gridWidth = Some(6),
        height = Some(500),
        chartType = Some(ChartType.Spline)
      )
    )

    val boxPlotSpec = BoxWidgetSpec(
      fieldName = field.name,
      displayOptions = BasicDisplayOptions(
        gridWidth = Some(6),
        height = Some(500)
      )
    )

    val basicStatsSpec = BasicStatsWidgetSpec(
      fieldName = field.name,
      displayOptions = BasicDisplayOptions(
        gridWidth = Some(2),
        height = Some(500)
      )
    )

    val widgetSpecs = Seq(distributionSpec, boxPlotSpec, basicStatsSpec)

    // aux function to generate widgets for a given filter
    def widgetsAux(filter: Filter, criterion: Criterion): Future[Seq[Option[Widget]]] =
      for {
        nameFieldMap <- createNameFieldMap(fieldRepo)(Seq(filter.conditions), widgetSpecs, Nil)
        widgets <- getViewWidgets(widgetSpecs, criterion, nameFieldMap.values, comparisonGenMethod)
      } yield
        widgets.toSeq

    for {
      multiCriteria <- Future.sequence(filters.map(filter => toCriterion(filter.conditions)))

      allWidgets <- Future.sequence(filters.zip(multiCriteria).map((widgetsAux(_, _)).tupled))

      anovaResult <- statsService.testAnovaForMultiCriteriaSorted(store, multiCriteria, Seq(field)).map(_.head._2)
    } yield {
      // aux function to extract widgets at given seq index
      def filterNameWidgets[W <: Widget](index: Int): Seq[(String, W)] =
        allWidgets.zip(filters).zipWithIndex.flatMap { case ((widgets, filter), filterIndex) =>
          widgets(index).map { widget =>
            val filterName = filter.name.getOrElse("Filter " + (filterIndex + 1))
            (filterName, widget.asInstanceOf[W])
          }
        }

      // (merged) distribution widget
      val distributionWidgets = filterNameWidgets[NumericalCountWidget[_]](0)
      val mergedDistributionData = distributionWidgets.map { case (filterName, widget) => (filterName, widget.data.head._2) }
      val distributionWidget = distributionWidgets.head._2.copy(data = mergedDistributionData)

      // (merged) box plot widget
      val boxWidgets = filterNameWidgets[BoxWidget[Any]](1)
      val mergedBoxPlotData = boxWidgets.map { case (filterName, widget) => (filterName, widget.data.head._2) }

      val pValue = anovaResult.map(result => formatDouble(result.pValue)).getOrElse("N/A")
      val isSignificant = anovaResult.map(result => result.pValue < 0.001).getOrElse(false)
      val FValue = anovaResult.map(result => formatDouble(result.FValue)).getOrElse("N/A")

      val boxTitle = s"<h4>ANOVA: p-Value = ${if(isSignificant) s"<b>$pValue</b>" else s"<i>$pValue</i>"}, F-Value = ${FValue}</h4>"

      def setData[T: Ordering](
        boxWidget: BoxWidget[T],
        newData: Seq[(String, Quartiles[T])]
      ): BoxWidget[T] = boxWidget.copy(data = newData, title = boxTitle)

      implicit val ordering = boxWidgets.head._2.ordering
      val boxWidget = setData(boxWidgets.head._2, mergedBoxPlotData)

      // basic stats widgets
      val filterNameBasicWidgets = filterNameWidgets[BasicStatsWidget](2)
      val basicStatsWidgets = filterNameBasicWidgets.zipWithIndex.map { case ((filterName, widget), index) =>
        if (filterNameBasicWidgets.size == 2 && index == 0) {
          val newDisplayOptions = widget.displayOptions.asInstanceOf[BasicDisplayOptions].copy(gridOffset = Some(1))
          widget.copy(title = filterName, displayOptions = newDisplayOptions)
        } else
          widget.copy(title = filterName)
      }

      // relative distributionWidget
      val relativeDistributionWidget = distributionWidget.copy(useRelativeValues = true)

      // ANOVA widget
      val widgets = Seq(distributionWidget, boxWidget, relativeDistributionWidget) ++ basicStatsWidgets
      val jsons = widgetsToJsons(widgets)
      Seq(jsons)
    }
  }

  private def categoricalComparisonWidgetsAsJson(
    filters: Seq[Filter],
    field: Field
  ): Future[Seq[JsArray]] = {
    val distributionSpec = DistributionWidgetSpec(
      fieldName = field.name,
      groupFieldName = None,
      displayOptions = MultiChartDisplayOptions(
        gridWidth = Some(6),
        height = Some(500),
        chartType = Some(ChartType.Column)
      )
    )

    val widgetSpecs = Seq(distributionSpec)
    val genMethod = if (field.isString) WidgetGenerationMethod.FullData else comparisonGenMethod

    // aux function to generate widgets for a given filter
    def widgetsAux(filter: Filter, criterion: Criterion): Future[Seq[Option[Widget]]] =
      for {
        nameFieldMap <- createNameFieldMap(fieldRepo)(Seq(filter.conditions), widgetSpecs, Nil)
        widgets <- getViewWidgets(widgetSpecs, criterion, nameFieldMap.values, genMethod)
      } yield
        widgets.toSeq

    for {
      multiCriteria <- Future.sequence(filters.map(filter => toCriterion(filter.conditions)))

      multiCriteriaWithFieldNonNull = multiCriteria.map(_ AND NotEqualsNullCriterion(field.name))

      allWidgets <- Future.sequence(filters.zip(multiCriteriaWithFieldNonNull).map((widgetsAux(_, _)).tupled))

      chiSquareResult <- statsService.testChiSquareForMultiCriteriaSorted(store, multiCriteriaWithFieldNonNull, Seq(field)).map(_.head._2)
    } yield {
      // distribution widget
      val distributionWidgets = allWidgets.zip(filters).zipWithIndex.map { case ((widgets, filter), filterIndex) =>
        widgets(0).map { widget =>
          val filterName = filter.name.getOrElse("Filter " + (filterIndex + 1))
          (filterName, widget.asInstanceOf[CategoricalCountWidget])
        }
      }.flatten

      val mergedDistributionData = distributionWidgets.map { case (filterName, widget) => (filterName, widget.data.head._2) }
      val nonZeroCountSeriesSorted = sortCountSeries(None)(mergedDistributionData)
      val distributionWidget = distributionWidgets.head._2.copy(data = nonZeroCountSeriesSorted)

      // chi-square
      val pValue = chiSquareResult.map(result => formatDouble(result.pValue)).getOrElse("N/A")
      val isSignificant = chiSquareResult.map(result => result.pValue < 0.001).getOrElse(false)
      val stats = chiSquareResult.map(result => formatDouble(result.statistics)).getOrElse("N/A")

      val textualTitle = s"Chi-Square: p-Value = ${if(isSignificant) s"<b>$pValue</b>" else s"<i>$pValue</i>"}, stats = ${stats}"

      // textual distribution widget
      val textualDistributionWidget = distributionWidget.copy(displayOptions = distributionWidget.displayOptions.copy(isTextualForm = true), title = textualTitle)

      val widgets = Seq(distributionWidget, textualDistributionWidget)
      val jsons = widgetsToJsons(widgets)
      Seq(jsons)
    }
  }

  override def getLineChart(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
    for {
      // get the filter (with labels), data set name, the data space tree, and the setting
      (filter, dataSetName, dataSpaceTree, setting) <- getFilterAndEssentials(filterOrId)
    } yield

      render {
        case Accepts.Html() => Ok(dataset.lineChart(
          dataSetName,
          filter,
          setting,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("The function getLineChart function doesn't support JSON response.")
      }
    }.recover(handleExceptions("a line chart"))
  }

  override def calcLineChart(
    xFieldName: String,
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    val yFieldNames = request.body.asFormUrlEncoded.flatMap(_.get("yFieldNames[]")).getOrElse(Nil)

    {
      for {
        // the x field
        xField <- fieldRepo.get(xFieldName)

        // y fields
        yFields <- getFields(yFieldNames)

        // generate the required widget(s)
        response <- yFields match {
          case Nil =>
            Future(BadRequest(s"No y fields provided."))

          case _ =>
            val widgetSpecs = Seq(
              XLineWidgetSpec(
                xFieldName,
                yFieldNames,
                groupFieldName,
                displayOptions = BasicDisplayOptions(
                  gridWidth = Some(12),
                  height = Some(550)
                )
              )
            )

            getFilterAndWidgetsCallbackAux(
              widgetSpecs,
              xLineChartGenMethod,
              filterOrId
            )
        }
      } yield
        response
      }.recover(handleExceptionsWithErrorCodes("Line chart"))
  }

  private def formatDouble(x: Double) = x match {
    case 0 => "0"
    case 1 => "1"
    case x if x < 0.00001 => f"$x%3.2E"
    case _ => f"$x%1.5f"
  }

  override def generateTable(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId,
    tableSelection: Boolean
  ) = AuthAction { implicit request =>
    val filterFuture = filterRepo.resolve(filterOrId)
    val fieldsFuture = getFields(fieldNames)

    {
      for {
        // use a given filter conditions or load one
        resolvedFilter <- filterFuture

        // get the fields
        fields <- fieldsFuture

        // create a name->field map
        nameFieldMap = fields.map(field => (field.name, field)).toMap

        // resolve criteria
        criteria <- toCriterion(resolvedFilter.conditions)

        // retrieve all the table items
        tableItems <- getTableItems(store)(page, orderBy, criteria, nameFieldMap, fieldNames, pageLimit)

        // get the total count
        count <- store.count(criteria)
      } yield {
        val tablePage = Page(tableItems, page, page * pageLimit, count, orderBy)
        val fieldsInOrder = fieldNames.map(nameFieldMap.get).flatten

        Ok(dataset.dataSetTable(tablePage, Some(resolvedFilter), fieldsInOrder, true, tableSelection))
      }
    }.recover(handleExceptionsWithErrorCodes("a generateTable"))
  }

  override def generateTableWithFilter(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    {
      for {
        // use a given filter conditions or load one
        resolvedFilter <- filterRepo.resolve(filterOrId)

        // resolve criteria
        criteria <- toCriterion(resolvedFilter.conditions)

        // create a name -> field map of all the referenced fields for a quick lookup
        nameFieldMap <- createNameFieldMap(fieldRepo)(Seq(resolvedFilter.conditions), Nil, fieldNames)

        // retrieve all the table items
        tableItems <- getTableItems(store)(page, orderBy, criteria, nameFieldMap, fieldNames, pageLimit)

        // get the total count
        count <- store.count(criteria)
      } yield {
        // table
        val tablePage = Page(tableItems, page, page * pageLimit, count, orderBy)
        val fieldsInOrder = fieldNames.map(nameFieldMap.get).flatten
        val table = dataset.dataSetTable(tablePage, Some(resolvedFilter), fieldsInOrder, true, true)

        // filter model / condition panel
        val newFilter = setFilterLabels(resolvedFilter, nameFieldMap)
        val conditionPanel = views.html.filter.conditionPanel(Some(newFilter))
        val filterModel = Json.toJson(resolvedFilter.conditions)

        val jsonResponse = Ok(Json.obj(
          "conditionPanel" -> conditionPanel.toString(),
          "filterModel" -> filterModel,
          "table" -> table.toString()
        ))

        render {
          case Accepts.Html() => jsonResponse
          case Accepts.Json() => jsonResponse
        }
      }
    }.recover(handleExceptionsWithErrorCodes("a generateTableWithFilter"))
  }

  override def getIndependenceTest(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
    val dataSetNameTreeSettingFuture = getDataSetNameTreeAndSetting(dsa)
    val filterFuture = filterRepo.resolve(filterOrId)

    for {
    // get the data set name, the data space tree, and the setting
      (dataSetName, dataSpaceTree, setting) <- dataSetNameTreeSettingFuture

      // use a given filter conditions or load one
      resolvedFilter <- filterFuture

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
    } yield {
      // get a new filter
      val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

      render {
        case Accepts.Html() => Ok(dataset.independenceTest(
          dataSetName,
          newFilter,
          setting,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("GetIndependenceTest function doesn't support JSON response.")
      }
    }
  }.recover(handleExceptions("an independence test"))
  }

  override def testIndependence(
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val (inputFieldNames, targetFieldName) = request.body.asFormUrlEncoded.map { inputs =>
      val x = inputs.get("inputFieldNames[]").getOrElse(Nil)
      val y = inputs.get("targetFieldName").map(_.head)
      (x, y)
    }.getOrElse((Nil, None))

    if (inputFieldNames.isEmpty || targetFieldName.isEmpty)
      Future(BadRequest("No input provided."))
    else
      testIndependenceAux(targetFieldName.get, inputFieldNames, filterOrId).recover(
        handleExceptionsWithErrorCodes("an independence test")
      )
  }

  private def testIndependenceAux(
    targetFieldName: String,
    inputFieldNames: Seq[String],
    filterOrId: FilterOrId
  ): Future[Result] = {
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(targetFieldName)).toSet.toSeq
      else
        Nil

    for {
      // use a given filter conditions or load one
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // criterion
      criterion <- toCriterion(resolvedFilter.conditions)

      // add a target-field-undefined criterion if needed
      finalCriteria = if (independenceTestKeepUndefined) criterion else criterion AND NotEqualsNullCriterion(targetFieldName)

      // fields
      fields <- dsa.fieldStore.find(FieldIdentity.name #-> explFieldNamesToLoads)

      // run independence tests
      resultsSorted <- {
        val inputFields = fields.filterNot(_.name.equals(targetFieldName)).toSeq
        val outputField = fields.find(_.name.equals(targetFieldName)).get
        statsService.testIndependenceSorted(dsa.dataSetStore, finalCriteria, inputFields, outputField, independenceTestKeepUndefined)
      }
    } yield {
      val fieldNameLabelMap = fields.map(field => (field.name, field.labelOrElseName)).toMap
      val nameFieldMap = fields.map(field => (field.name, field)).toMap

      // filter model / condition panel
      val newFilter = setFilterLabels(resolvedFilter, nameFieldMap)
      val conditionPanel = views.html.filter.conditionPanel(Some(newFilter))
      val filterModel = Json.toJson(newFilter.conditions)

      // label results and serialize to jsons
      val labelResults = resultsSorted.flatMap { case (field, result) =>
        val fieldLabel = fieldNameLabelMap.get(field.name).get
        result.map((fieldLabel, _))
      }

      implicit val stringTestTupleFormat = TupleFormat[String, IndependenceTestResult]
      val jsonResults = Json.toJson(labelResults)

      // prepare a full response (with a filter and stuff)
      Ok(Json.obj(
        "conditionPanel" -> conditionPanel.toString(),
        "filterModel" -> filterModel,
        "results" -> jsonResults
      ))
    }
  }

  override def getCategoriesWithFieldsAsTreeNodes(
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val start = new ju.Date()

    val settingFuture = dsa.setting

    val filterFuture = filterRepo.resolve(filterOrId)

    val allCategoriesFuture = categoryRepo.find()

    val fieldsFuture = fieldRepo.find(
//      criteria = Seq("categoryId" #!= None),
      projection = Seq("name", "label", "categoryId", "fieldType")
    )

    for {
      // get the data set setting
      setting <- settingFuture

      // get the filter
      filter <- filterFuture

      // retrieve all the categories
      categories <- allCategoriesFuture

      // retrieve all the fields defined (with and without category)
      fields <- fieldsFuture

      // convert conditions to criterion
      criterion <- toCriterion(filter.conditions)

      // count the not-null elements for each field
      fieldNotNullCounts <-
        if (setting.filterShowNonNullCount) {
          seqFutures(fields.toSeq.grouped(1000)) { fieldGroup =>
            Future.sequence(
              fieldGroup.map(field =>
                // count not null
                dsa.dataSetStore.count(criterion AND (field.name #!@)).map(count => (field, Some(count)))
              )
            )
          }
        } else
          Future(Seq(fields.map(field => (field, None))))

    } yield {
      val jsTreeNodes =
        categories.map(JsTreeNode.fromCategory) ++
          fieldNotNullCounts.flatten.map { case (field, count) => JsTreeNode.fromField(field, count, setting.filterShowFieldStyle) }

      logger.info("Categories with fields and counts retrieved in " + (new ju.Date().getTime - start.getTime) + " ms.")
      Ok(Json.toJson(jsTreeNodes))
    }
  }

  override def getClusterization = AuthAction { implicit request =>
    {
      for {
        // get the data set name, data space tree and the data set setting
        (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(dsa)
      } yield {
        render {
          case Accepts.Html() => Ok(dataset.cluster(
            dataSetName,
            setting,
            tree
          ))
          case Accepts.Json() => BadRequest("getUnsupervisedLearning function doesn't support JSON response.")
        }
      }
    }.recover(handleExceptions("a clusterization"))
  }

  private def getFilterAndEssentials(
    filterOrId: FilterOrId)(
    implicit request: AuthenticatedRequest[_]
  ): Future[(Filter, String, Traversable[DataSpaceMetaInfo], DataSetSetting)] = {
    val dataSetNameTreeSettingFuture = getDataSetNameTreeAndSetting(dsa)
    val filterFuture = filterRepo.resolve(filterOrId)

    for {
    // get the data set name, the data space tree, and the setting
      (dataSetName, dataSpaceTree, setting) <- dataSetNameTreeSettingFuture

      // use a given filter conditions or load one
      resolvedFilter <- filterFuture

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
    } yield {
      // get a new filter
      val filter = setFilterLabels(resolvedFilter, fieldNameMap)
      (filter, dataSetName, dataSpaceTree, setting)
    }
  }

  override def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorScalerType.Value],
    pcaDims: Option[Int]
  ) = AuthAction { implicit request => {
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(JsObjectIdentity.name)).toSet.toSeq
      else
        Nil

    val mlModelFuture = clusteringRepo.get(mlModelId)
    val criterionFuture = loadCriterion(filterId)

    for {
      mlModel <- mlModelFuture
      criterion <- criterionFuture
      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, explFieldNamesToLoads, criterion)
    } yield
      mlModel match {
        case Some(mlModel) =>
          val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))
          val jsonsWithStringIds = jsons.map { json =>
            val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
            json.+(JsObjectIdentity.name, JsString(id.stringify))
          }

          val (idClasses, idPCA12s) = mlService.clusterAndGetPCA12(jsonsWithStringIds, fieldNameAndSpecs, mlModel, featuresNormalizationType, pcaDims)

          val numericFields = fields.filter(field => !field.fieldTypeSpec.isArray && numericTypes.contains(field.fieldType))

          val idClassMap = idClasses.toMap

          // PCA 1-2 Scatter
          val pcaDisplayOptions = BasicDisplayOptions(gridWidth = Some(10), gridOffset = Some(1), height = Some(600))

          val pca12WidgetData = idPCA12s.map { case (id, pca12) =>
            (idClassMap.get(id).get.toString, pca12)
          }.toGroupMap.toSeq.sortBy(_._1)

          val pca12Scatter = ScatterWidget("PCA", "PC1", "PC2", "PC1", "PC2", FieldTypeId.Double, FieldTypeId.Double, pca12WidgetData, pcaDisplayOptions)

          val jsonsWithClasses = jsonsWithStringIds.flatMap { json =>
            val stringId = (json \ JsObjectIdentity.name).as[String]
            idClassMap.get(stringId).map(clazz => json.+("cluster_class", JsNumber(clazz)))
          }

          // Other scatters (fields pairwise_
          def createScatter(
            xField: Field,
            yField: Field
          ): Option[Widget] = {
            val clusterClassField = Field("cluster_class", Some("Cluster Class"), FieldTypeId.Integer, false)

            val displayOptions = BasicDisplayOptions(gridWidth = Some(4), gridOffset = Some(1), height = Some(350))
            val widgetSpec = ScatterWidgetSpec(xField.name, yField.name, Some(clusterClassField.name), None, displayOptions)

            // generate a scatter widget
            widgetService.genFromFullData(widgetSpec, jsonsWithClasses, NoCriterion, Seq(xField, yField, clusterClassField)).head
          }

          // Transform Scatters into JSONs
          val scatters = numericFields.combinations(2).take(64).flatMap { fields => createScatter(fields(0), fields(1)) }

          implicit val jsonWrites = Widget.writes
          val scatterJsons = (Seq(pca12Scatter) ++ scatters).map(scatter =>
            Json.toJson(scatter)
          )

          val classSizeJsons = idClasses.groupBy(_._2).toSeq.sortBy(_._1).map { case (_, values) => JsNumber(values.size) }

          // Independence Test
          val numValues = idClassMap.values.map(classId => (classId.toString, classId.toString)).toMap
          val clusterClassField = Field("cluster_class", Some("Cluster Class"), FieldTypeId.Enum, false, numValues)
          val fieldNameLabelMap = fields.map(field => (field.name, field.labelOrElseName)).toMap

          val testResults = statsService.testIndependenceSortedJson(jsonsWithClasses, fields, clusterClassField)

          implicit val stringTestTupleFormat = TupleFormat[String, IndependenceTestResult]

          // Create JSON results
          val jsonResults = testResults.flatMap { case (field, result) =>
            val fieldLabel = fieldNameLabelMap.get(field.name).get

            result.map(result =>
              Json.toJson((fieldLabel, result))
            )
          }

          Ok(Json.obj(
            "classSizes" -> JsArray(classSizeJsons),
            "scatters" -> JsArray(scatterJsons),
            "testResults" -> JsArray(jsonResults)
          ))

        case None =>
          BadRequest(s"ML unsupervised learning model with id ${mlModelId.stringify} not found.")
      }
  }.recover(handleExceptionsWithErrorCodes("a clustering"))
  }

  private def loadCriterion(filterId: Option[BSONObjectID]): Future[Criterion] =
    for {
      filter <- filterId match {
        case Some(filterId) => filterRepo.get(filterId)
        case None => Future(None)
      }

      criterion <- filter match {
        case Some(filter) => toCriterion(filter.conditions)
        case None => Future(And())
      }
    } yield
      criterion

  @Deprecated
  override def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = Action.async { implicit request =>
    for {
      fields <- fieldRepo.find(
        criterion = fieldTypeIds match {
          case Nil => NoCriterion
          case _ => "fieldType" #-> fieldTypeIds
        },
//        sort = Seq(AscSort("name")),
        projection = Seq("name", "label", "fieldType")
      )
    } yield {
      implicit val fieldFormat = DataSetFormattersAndIds.fieldFormat
      Ok(Json.toJson(fields))
    }
  }

  private implicit val optionFormat = new OptionFormat[String]
  private implicit val tupleFormat = TupleFormat[String, Option[String]]

  override def getFieldNamesAndLabels(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = Action.async { implicit request =>
    for {
      valueMaps <- fieldRepo.findAsValueMap(
        criterion = fieldTypeIds match {
          case Nil => NoCriterion
          case _ => "fieldType" #-> fieldTypeIds
        },
        //        sort = Seq(AscSort("name")),
        projection = Seq("name", "label")
      )
    } yield {
      val fieldNameAndLabels = valueMaps.map { valueMap =>
        val name = valueMap.getAs[String]("name").get
        val label = valueMap.getAs[Option[String]]("label").flatten
        (name, label)
      }.toSeq

      Ok(Json.toJson(fieldNameAndLabels))
    }
  }

  override def getField(fieldName: String) = Action.async { implicit request =>
    for {
      field <- fieldRepo.get(fieldName)
    } yield {
      implicit val fieldFormat = DataSetFormattersAndIds.fieldFormat
      Ok(Json.toJson(field))
    }
  }

  override def getFieldTypeWithAllowedValues(fieldName: String) = Action.async { implicit request =>
    for {
      field <- fieldRepo.get(fieldName)
    } yield
      field match {
        case Some(field) =>
          val allowedValues = getAllowedValues(field)
          implicit val tupleFormat = TupleFormat[String, String]
          val json = Json.obj("fieldType" -> field.fieldType, "isArray" -> field.isArray, "allowedValues" -> allowedValues)
          Ok(json)

        case None => BadRequest(s"Field $fieldName does not exist.")
      }
  }

  private def getAllowedValues(field: Field): Traversable[(String, String)] =
    field.fieldType match {
      case FieldTypeId.Enum => field.enumValues.toSeq.sortBy(_._1)
      case FieldTypeId.Boolean => Seq(
        "true" -> field.displayTrueValue.getOrElse("true"),
        "false" -> field.displayFalseValue.getOrElse("false")
      )
      case _ => Nil
    }

  override def getFieldValue(id: BSONObjectID, fieldName: String) = Action.async { implicit request =>
//    for {
//      items <- repo.find(criteria = Seq("_id" #== id), projection = Seq(fieldName))
//    } yield
//      items.headOption match {
//        case Some(item) => Ok((item \ fieldName).get)
//        case None => BadRequest(s"Item '${id.stringify}' not found.")
//      }
    for {
      item <- store.get(id)
    } yield
      item match {
        case Some(item) => Ok((item \ fieldName).getOrElse(JsNull))
        case None => BadRequest(s"Item '${id.stringify}' not found.")
      }
  }

  override def getSeriesProcessingSpec = AuthAction { implicit request =>
    for {
      // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(dsa)
    } yield
      Ok(dataset.processSeries(dataSetName, seriesProcessingSpecForm, setting, tree))
  }

  override def runSeriesProcessing = AuthAction { implicit request =>
    seriesProcessingSpecForm.bindFromRequest.fold(
      { formWithErrors =>
        for {
          // get the data set name, data space tree and the data set setting
          (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(dsa)
        } yield {
          BadRequest(dataset.processSeries(dataSetName, formWithErrors, setting, tree))
        }
      },
      spec =>
        transformationService.processSeriesAndSaveDataSet(spec).map( _ =>
          Redirect(router.getSeriesProcessingSpec).flashing("success" -> s"Series processing finished.")
        )
    )
  }

  override def getSeriesTransformationSpec = AuthAction { implicit request =>
    for {
    // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(dsa)
    } yield
      Ok(dataset.transformSeries(dataSetName, seriesTransformationSpecForm, setting, tree))
  }

  override def runSeriesTransformation = AuthAction { implicit request =>
    seriesTransformationSpecForm.bindFromRequest.fold(
      { formWithErrors =>
        for {
        // get the data set name, data space tree and the data set setting
          (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(dsa)
        } yield {
          BadRequest(dataset.transformSeries(dataSetName, formWithErrors, setting, tree))
        }
      },
      spec =>
        for {
          _ <- transformationService.transformSeriesAndSaveDataSet(spec)
        } yield {
          Redirect(router.getSeriesTransformationSpec).flashing("success" -> s"Series transformation finished.")
        }
    )
  }

  private def getFieldLabelMap(fieldNames : Traversable[String]): Future[Map[String, String]] = {
    val futureFieldLabelPairs : Traversable[Future[Option[(String, String)]]]=
      fieldNames.map { fieldName =>
        fieldRepo.get(fieldName).map { fieldOption =>
          fieldOption.flatMap{_.label}.map{ label => (fieldName, label)}
        }
      }

    Future.sequence(futureFieldLabelPairs).map{ _.flatten.toMap }
  }

  private def filterSubCriteriaMap(widgetSpecs: Traversable[WidgetSpec])  =
    Future.sequence(
      widgetSpecs.map(_.subFilterId).flatten.toSet.map { subFilterId: BSONObjectID =>

        filterRepo.resolve(Right(subFilterId)).flatMap(resolvedFilter =>
          toCriterion(resolvedFilter.conditions).map(criteria =>
            (subFilterId, criteria)
         )
        )
      }
    )

  //////////////////////
  // Export Functions //
  //////////////////////

  /**
    * Generate and content of TRANSMART data file and create a download.
    *
    * @param delimiter Delimiter for output file.
    * @return View for download.
    */
  override def exportTranSMARTDataFile(
    delimiter : String,
    visitFieldName: Option[String],
    replaceEolWithSpace: Boolean
  ) = AuthAction { implicit request =>
    {
      for {
        setting <- dsa.setting

        fileContent <- generateTranSMARTDataFile(
          tranSMARTDataFileName,
          delimiter,
          setting.keyFieldName,
          setting.exportOrderByFieldName,
          visitFieldName,
          replaceEolWithSpace
        )
      } yield
        stringToFile(fileContent, tranSMARTDataFileName)
    }.recover(
      handleExceptions("an export TranSMART data file")
    )
  }

  /**
    * Generate content of TRANSMART mapping file and create a download.
    *
    * @param delimiter Delimiter for output file.
    * @return View for download.
    */
  override def exportTranSMARTMappingFile(
    delimiter: String,
    visitFieldName: Option[String],
    replaceEolWithSpace: Boolean
  ) = AuthAction { implicit request =>
    {
      for {
        setting <- dsa.setting

        fileContent <- generateTranSMARTMappingFile(
          tranSMARTDataFileName,
          delimiter,
          setting.keyFieldName,
          setting.exportOrderByFieldName,
          visitFieldName,
          replaceEolWithSpace
        )
      } yield
        stringToFile(fileContent, tranSMARTMappingFileName)
    }.recover(
      handleExceptions("an export TranSMART mapping file")
    )
  }

  /**
    * Generate  content of TRANSMART data file for download.
    *
    * @param dataFilename Name of output file.
    * @param delimiter Delimiter for output file.
    * @param orderBy Order of fields in data file.
    * @return VString with file content.
    */
  protected def generateTranSMARTDataFile(
    dataFilename: String,
    delimiter: String,
    keyFieldName: String,
    orderBy: Option[String],
    visitFieldName: Option[String],
    replaceEolWithSpace: Boolean
  ): Future[String] = {
    for {
      records <- store.find(sort = orderBy.fold(Seq[Sort]())(toSort))

      categories <- categoryRepo.find()

      categoryMap <- fieldNameCategoryMap(categories)

      fields <- fieldRepo.find()
    } yield {
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
      val replacements = if (replaceEolWithSpace) csvCharReplacements else Map()

      tranSMARTService.createClinicalDataFile(unescapedDelimiter, csvEOL, replacements)(
        records,
        keyFieldName,
        visitFieldName,
        categoryMap,
        fields.map(field => (field.name, ftf(field.fieldTypeSpec))).toMap
      )
    }
  }

  /**
    * Generate the content of TRANSMART mapping file for downnload.
    *
    * @param dataFilename Name of output file.
    * @param delimiter Delimiter for output file.
    * @param orderBy Order of fields in data file.
    * @return VString with file content.
    */
  protected def generateTranSMARTMappingFile(
    dataFilename: String,
    delimiter: String,
    keyFieldName: String,
    orderBy: Option[String],
    visitFieldName: Option[String],
    replaceEolWithSpace: Boolean
  ): Future[String] = {
    for {
      categories <- categoryRepo.find()

      categoryMap <- fieldNameCategoryMap(categories)

      fieldMap <- fieldLabelMap
    } yield {
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
      val replacements = if (replaceEolWithSpace) csvCharReplacements else Map()

      tranSMARTService.createMappingFile(unescapedDelimiter, csvEOL, replacements)(
        dataFilename,
        keyFieldName,
        visitFieldName,
        categoryMap,
        rootCategoryTree(categories),
        fieldMap
      )
    }
  }

  protected def fieldNameCategoryMap(
    categories: Traversable[Category]
  ): Future[Map[String, Category]] = {
    val idCategories = categories.map{ category =>
      (category._id.get, category)
    }

    for {
      fieldsWithCategories <- fieldRepo.find(
        criterion = "categoryId" #!= None,
        projection = Seq("name", "categoryId", "fieldType")
      )
    } yield {
      val idCategoriesMap = idCategories.toMap
      fieldsWithCategories.map(field =>
        (field.name, idCategoriesMap(field.categoryId.get))
      ).toMap
    }
  }

  protected def fieldLabelMap: Future[Map[String, Option[String]]] =
    for {
      fields <- fieldRepo.find(projection = Seq("name", "label", "fieldType"))
    } yield
      fields.map( field => (field.name, field.label)).toMap

  protected def rootCategoryTree(
    categories: Traversable[Category]
  ): Category = {
    val idCategoryMap = categories.map( category => (category._id.get, category)).toMap
    categories.foreach {category =>
      if (category.parentId.isDefined) {
        val parent = idCategoryMap(category.parentId.get)
        parent.addChild(category)
      }
    }

    val layerOneCategories = categories.filter(_.parentId.isEmpty).toSeq

    new Category("").setChildren(layerOneCategories)
  }
}

case class DataSetShowViewDataHolder(
  id: BSONObjectID,
  title: String,
  item: JsObject,
  listCall: Call,
  nonNullOnly: Boolean,
  fieldNameLabelAndRendererMap: Map[String, (String, JsReadable => String)],
  setting: DataSetSetting,
  dataSpaceMetaInfos: Traversable[DataSpaceMetaInfo]
)

object IndependenceTestResult {
  implicit val independenceTestResultFormat: Format[IndependenceTestResult] = new SubTypeFormat[IndependenceTestResult] (
    Seq(
      RuntimeClassFormat(Json.format[ChiSquareResult]),
      RuntimeClassFormat(Json.format[OneWayAnovaResult])
    )
  )
}