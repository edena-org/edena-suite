package org.edena.ada.web.controllers.dataset

import akka.actor.ActorSystem
import akka.stream.Materializer
import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.edena.core.util.toHumanReadableCamel
import org.edena.ada.server.models.{DistributionWidgetSpec, _}
import org.edena.ada.server.models.Filter.{FilterIdentity, FilterOrId}
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.edena.ada.web.models.Widget.scatterWidgetFormat
import org.edena.ada.server.dataaccess.dataset.DataSetAccessor
import play.api.Logger
import play.api.data.Forms._
import play.api.libs.json._
import play.api.mvc.{Action, Request}
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.services.DataSetService
import org.edena.ada.web.services.DataSpaceService
import org.edena.ada.server.services.ml._
import org.edena.ada.server.field.FieldUtil
import org.edena.ada.server.field.FieldUtil.caseClassToFlatFieldTypes
import org.edena.ada.web.controllers.core.AdaReadonlyControllerImpl
import org.edena.ada.web.controllers.core.{ExportableAction, WidgetRepoController}
import org.edena.ada.web.models.{ScatterWidget, Widget}
import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.json.OrdinalEnumFormat
import org.edena.core.{FilterCondition, Identity}
import org.edena.core.store.{And, Criterion, CrudStore, ReadonlyStore}
import org.edena.play.Page
import org.edena.core.store.Criterion._
import org.edena.core.field.FieldTypeId
import org.edena.play.controllers._
import org.edena.spark_ml.models.classification.ClassificationEvalMetric
import org.edena.spark_ml.models.result._
import org.edena.spark_ml.models.setting.RunSpec
import org.edena.store.json.JsObjectIdentity
import play.twirl.api.Html

import scala.reflect.runtime.universe.typeOf
import scala.reflect.runtime.universe.TypeTag
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

protected[controllers] abstract class MLRunControllerImpl[R <: MLResult : Format, ML](
  implicit override val typeTag: TypeTag[R], identity: Identity[ML, BSONObjectID], actorSystem: ActorSystem, materializer: Materializer
) extends AdaReadonlyControllerImpl[R, BSONObjectID]
    with MLRunController
    with WidgetRepoController[R]
    with ExportableAction[R] {

  protected def dsa: DataSetAccessor
  override protected val store: CrudStore[R, BSONObjectID]

  protected def dataSpaceService: DataSpaceService
  protected def dataSetService: DataSetService
  protected def mlMethodRepo: ReadonlyStore[ML, BSONObjectID]
  protected def mlMethodName: ML => String
  protected def mlService: MachineLearningService

  protected case class ResultExtra(dataSetId: String, mlModelName: Option[String], filterName: Option[String])
  protected implicit val resultExtraFormat = Json.format[ResultExtra]
  protected val extraFields = caseClassToFlatFieldTypes[ResultExtra]()

  protected val ftf = FieldTypeHelper.fieldTypeFactory()
  protected val logger = Logger // (this.getClass())

  override protected def format: Format[R] = implicitly[Format[R]]

  protected lazy val resultFields = caseClassToFlatFieldTypes[R]("-", Set(JsObjectIdentity.name) ++ excludedFieldNames)
  protected lazy val resultFieldNames = resultFields.map(_._1).toSeq

  protected def widgetSpecs: Seq[WidgetSpec]

  // export stuff
  protected val exportFileNamePrefix: String
  private val exportOrderByFieldName = "timeCreated"
  private def csvFileName = exportFileNamePrefix + dsa.dataSetId.replace(" ", "-") + ".csv"
  private def jsonFileName = exportFileNamePrefix + dsa.dataSetId.replace(" ", "-") + ".json"

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"

  // data set web context with all the routers
  protected implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dsa.dataSetId)
  protected val router: MLRunRouter

  override protected lazy val homeCall = router.plainList

  // show view and data

  override protected type ShowViewData = (
    String,
    R,
    DataSetSetting,
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getShowViewData(
    id: BSONObjectID,
    item: R
  ) = { request =>
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val dataSetNameFuture = dsa.dataSetName
    val settingFuture = dsa.setting

    for {
      dataSetName <- dataSetNameFuture
      tree <- treeFuture
      setting <- settingFuture
    } yield
      (dataSetName + " " + entityName, item, setting, tree)
  }

  // list view and data

  override protected type ListViewData = (
    String,
    String,
    Page[R],
    Seq[FilterCondition],
    Traversable[Widget],
    Map[String, String],
    Traversable[Field],
    Map[BSONObjectID, String],
    Map[BSONObjectID, String],
    DataSetSetting,
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[R],
    conditions: Seq[FilterCondition]
  ) = { implicit request: AuthenticatedRequest[_] =>
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val nameFuture = dsa.dataSetName

    val fieldNames = page.items.flatMap { result =>
      result.inputFieldNames ++ Seq(result.outputFieldName)
    }.toSet

    val fieldsFuture = getDataSetFields(fieldNames)

    val resultFieldsFuture = fieldCaseClassRepo.find()

    val mlModelIds = page.items.map(result => Some(result.mlModelId)).toSet

    val mlMethodsFuture = mlMethodRepo.find(identity.name #-> mlModelIds.toSeq)

    val filterIds = page.items.map(_.filterId).toSet

    val filtersFuture = dsa.filterStore.find(FilterIdentity.name #-> filterIds.toSeq)

    val widgetsFuture = toCriterion(conditions).flatMap( criteria =>
      widgets(widgetSpecs, criteria)
    )

    val settingFuture = dsa.setting

    for {
      tree <- treeFuture

      dataSetName <- nameFuture

      fields <- fieldsFuture

      resultFields <- resultFieldsFuture

      mlMethods <- mlMethodsFuture

      filters <- filtersFuture

      widgets <- widgetsFuture

      setting <- settingFuture
    } yield {
      val fieldNameLabelMap = fields.map(field => (field.name, field.labelOrElseName)).toMap
      val mlMethodIdNameMap = mlMethods.map(mlMethods => (identity.of(mlMethods).get, mlMethodName(mlMethods))).toMap
      val filterIdNameMap = filters.map(filter => (filter._id.get, filter.name.get)).toMap

      (dataSetName + " " + entityName, dataSetName, page, conditions, alterWidgets(widgets.flatten), fieldNameLabelMap, resultFields, mlMethodIdNameMap, filterIdNameMap, setting, tree)
    }
  }

  protected def alterWidgets(widgets: Traversable[Widget]) =
    widgets.map { widget =>
      widget match {
        // fixing the group name for scatters (BSON object id)
        case w: ScatterWidget[_, _] =>
          w.copy(data =
            w.data.map { case (group, rest) =>
              (group.substring(9, group.length - 2), rest)
            }
          )
        case _ => widget
      }
    }

  // create view and data

  protected type CreateViewData = (
    String,
    DataSetSetting,
    Traversable[DataSpaceMetaInfo]
  )

  override def create = AuthAction { implicit request =>
    {
      for {
      // get the data set name, data space tree and the data set setting
       (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
      } yield {
        val context = implicitly[DataSetWebContext]

        render {
          case Accepts.Html() => Ok(createView(context)(dataSetName, setting, tree))
          case Accepts.Json() => BadRequest("ML run create function doesn't support JSON response.")
        }
      }
    }.recover(handleExceptions("create"))
  }

  protected def createView: DataSetWebContext => CreateViewData => Html

  protected def loadCriterion(filterId: Option[BSONObjectID]): Future[Criterion] =
    for {
      filter <- filterId match {
        case Some(filterId) => dsa.filterStore.get(filterId)
        case None => Future(None)
      }

      criterion <- filter match {
        case Some(filter) => FieldUtil.toCriterion(dsa.fieldStore, filter.conditions)
        case None => Future(And())
      }
    } yield
      criterion

  protected def getDataSetFields(fieldNames: Traversable[String]) =
    if (fieldNames.nonEmpty)
      dsa.fieldStore.find(FieldIdentity.name #-> fieldNames.toSeq)
    else
      Future(Nil)

  protected def resultsExtended: Future[Traversable[(R, ResultExtra)]] = {
    for {
      // get the results
      results <- store.find()

      // add some extra stuff for easier reference (model and filter name)
      resultsWithExtra <- Future.sequence(
        results.map { result =>
          val classificationFuture = mlMethodRepo.get(result.mlModelId)
          val filterFuture = result.filterId.map(dsa.filterStore.get).getOrElse(Future(None))

          for {
            mlModel <- classificationFuture
            filter <- filterFuture
          } yield
            (result, ResultExtra(dsa.dataSetId, mlModel.map(mlMethodName), filter.flatMap(_.name)))
        }
      )
    } yield
      resultsWithExtra
  }

  protected def resultsToJson[E <: Enumeration](
    evalMetric: E)(
    evalMetricStatsMap: Map[E#Value, (MetricStatsValues, Option[MetricStatsValues], Option[MetricStatsValues])]
  ): JsArray = {
    val metricJsons = evalMetric.values.toSeq.sorted.flatMap { metric =>
      evalMetricStatsMap.get(metric).map { case (trainingStats, testStats, replicationStats) =>
        Json.obj(
          "metricName" -> toHumanReadableCamel(metric.toString),
          "trainEvalRate" -> trainingStats.mean,
          "testEvalRate" -> testStats.map(_.mean),
          "replicationEvalRate" -> replicationStats.map(_.mean)
        )
      }
    }

    JsArray(metricJsons)
  }

  protected def getDataSetNameTreeAndSetting(
    implicit request: AuthenticatedRequest[_]
  ): Future[(String, Traversable[DataSpaceMetaInfo], DataSetSetting)] = {
    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser
    val settingFuture = dsa.setting

    for {
    // get the data set name
      dataSetName <- dataSetNameFuture

      // get the data space tree
      dataSpaceTree <- treeFuture

      // get the data set setting
      setting <- settingFuture
    } yield
      (dataSetName, dataSpaceTree, setting)
  }

  override protected def filterValueConverters(
    fieldNames: Traversable[String]
  ) =
    if (fieldNames.nonEmpty)
      FieldUtil.valueConverters(fieldCaseClassRepo, fieldNames)
    else
      Future(Map())

  override def delete(id: BSONObjectID) = Action.async { implicit request =>
    store.delete(id).map { _ =>
      render {
        case Accepts.Html() => Redirect(router.plainList).flashing("success" -> s"Item ${id.stringify} has been deleted")
        case Accepts.Json() => Ok(Json.obj("message" -> "Item successfully deleted", "id" -> id.toString))
      }
    }.recover(handleExceptionsWithId("delete", id))
  }

  // exporting

  def exportToDataSet(
    targetDataSetId: Option[String],
    targetDataSetName: Option[String]
  ) = Action.async { implicit request =>
    val newDataSetId = targetDataSetId.map(_.replace(' ', '_')).getOrElse(dsa.dataSetId + "_" + entityNameKey)

    for {
      // collect all the results
      allResults <- resultsExtended

      // data set name
      dataSetName <- dsa.dataSetName

      // new data set name
      newDataSetName = targetDataSetName.getOrElse(dataSetName + " " + entityName)

      // register target dsa
      targetDsa <-
        dataSetService.register(
          dsa,
          newDataSetId,
          newDataSetName,
          StorageType.Mongo
        )

      // update the dictionary
      _ <- {
        val newFields = (resultFields ++ extraFields).map { case (name, fieldTypeSpec) =>
          val roundedFieldSpec =
            if (fieldTypeSpec.fieldType == FieldTypeId.Double)
              fieldTypeSpec.copy(displayDecimalPlaces = Some(3))
            else
              fieldTypeSpec

          val stringEnums = roundedFieldSpec.enumValues.map { case (from, to) => (from.toString, to) }
          val label = toHumanReadableCamel(name.replaceAllLiterally("-", " ").replaceAllLiterally("Stats", ""))
          Field(name, Some(label), roundedFieldSpec.fieldType, roundedFieldSpec.isArray, stringEnums, roundedFieldSpec.displayDecimalPlaces)
        }
        dataSetService.updateFields(newDataSetId, newFields, false, true)
      }

      // delete the old results (if any)
      _ <- targetDsa.dataSetStore.deleteAll

      // save the results
      _ <- targetDsa.dataSetStore.save(
        allResults.map { case (result, extraResult) =>

          val resultJson = Json.toJson(result)(exportFormat).as[JsObject]
          val finalResultJson = alterExportJson(resultJson)

          val extraResultJson = Json.toJson(extraResult).as[JsObject]
          finalResultJson ++ extraResultJson
        }
      )
    } yield
      Redirect(router.plainList).flashing("success" -> s"$entityName results successfully exported to $newDataSetName.")
  }

  protected def exportFormat: Format[R]

  protected def alterExportJson(resultJson: JsObject): JsObject = resultJson

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

    val fieldsNames = if (tableColumnsOnly) listViewColumns.getOrElse(throw new AdaException("No list/table column views specified.")) else resultFieldNames

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

  protected def replicationCriterion(runSpec: RunSpec) =
    runSpec.ioSpec.replicationFilterId.map { filterId =>
      loadCriterion(Some(filterId)).map(Some(_))
    }.getOrElse(
      Future(None)
    )
}