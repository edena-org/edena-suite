package org.edena.ada.web.controllers.dataset.dataimport

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}
import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.{DataSetImportStore, MessageStore}
import org.edena.ada.server.models.dataimport.DataSetImport.{DataSetImportIdentity, dataSetImportFormat}
import org.edena.ada.server.models.dataimport._
import org.edena.ada.server.models.DataSpaceMetaInfo
import org.edena.ada.server.models.ScheduledTime.fillZeroesIfNeeded
import org.edena.ada.server.services.{DataSetService, StaticLookupCentral}
import org.edena.ada.server.services.ServiceTypes._
import org.edena.ada.server.{AdaException, AdaParseException}
import org.edena.ada.web.controllers.core.AdaCrudControllerImpl
import org.edena.ada.web.services.DataSpaceService
import org.edena.core.FilterCondition
import org.edena.core.store.AscSort
import org.edena.core.util.retry
import org.edena.play.Page
import org.edena.play.controllers._
import org.edena.play.formatters._
import org.edena.play.util.WebUtil.getRequestParamValueOptional
import play.api.Logging
import play.api.data.{Form, FormError}
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import views.html.{datasetimport => view}

import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

class DataSetImportController @Inject()(
  repo: DataSetImportStore,
  dataSetService: DataSetService,
  dataSetCentralImporter: DataSetCentralImporter,
  dataSetImportScheduler: DataSetImportScheduler,
  dataSetImportFormViewsCentral: StaticLookupCentral[DataSetImportFormViews[DataSetImport]],
  dataSpaceService: DataSpaceService,
  messageRepo: MessageStore,
  val controllerComponents: ControllerComponents
  ) extends AdaCrudControllerImpl[DataSetImport, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[DataSetImport, BSONObjectID]
    with HasFormShowEqualEditView[DataSetImport, BSONObjectID] {


  override protected val entityNameKey = "dataSetImport"
  override protected def formatId(id: BSONObjectID) = id.stringify

  private lazy val importFolder = configuration.getOptional[String]("datasetimport.import.folder").getOrElse {
    val folder = new java.io.File("dataImports/").getAbsolutePath
    val path = Paths.get(folder)
    // create if doesn't exist
    if (!Files.exists(path)) Files.createDirectory(path)
    folder
  }

  private lazy val importRetryNum = configuration.getOptional[Int]("datasetimport.retrynum").getOrElse(3)

  override protected val createEditFormViews = dataSetImportFormViewsCentral()
  private val importClassNameMap: Map[Class[_], String] = createEditFormViews.map(x => (x.man.runtimeClass, x.displayName)).toMap

  // default form... unused
  override protected val form = CsvFormViews.form.asInstanceOf[Form[DataSetImport]]

  override protected lazy val homeCall = routes.DataSetImportController.find()

  override protected type ListViewData = (
    Page[DataSetImport],
    Seq[FilterCondition],
    Map[Class[_], String],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[DataSetImport],
    conditions: Seq[FilterCondition]
  ) = { request =>
    for {
      tree <- dataSpaceService.getTreeForCurrentUser(request)
    } yield
      (page, conditions, importClassNameMap, tree)
  }

  override protected def listView = { implicit ctx => (view.list(_, _, _, _)).tupled}

  override def create(concreteClassName: String) = restrictAny(super.create(concreteClassName))

  def execute(id: BSONObjectID) = restrictAny {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Data set import #${id.stringify} not found"))
      ) { importInfo =>
          val start = new Date()

          retry(s"Data set '${importInfo.dataSetName}' import failed: ", logger.warn(_), importRetryNum)(
            dataSetCentralImporter(importInfo)
          ).map { _ =>
            val execTimeSec = (new Date().getTime - start.getTime) / 1000
            render {
              case Accepts.Html() => Ok(s"Data set '${importInfo.dataSetName}' has been imported in $execTimeSec sec(s).")
              case Accepts.Json() => Created(Json.obj("message" -> s"Data set has been imported in $execTimeSec sec(s)", "name" -> importInfo.dataSetName))
            }
          }.recover(handleExceptionsWithErrorCodes("data set import"))
      })
  }

  override protected def formFromRequest(
    implicit request: Request[AnyContent]
  ): Form[DataSetImport] = {
    val filledForm = super.formFromRequest(request)

    // aux function to add param values to a form
    def addToForm(
      form: Form[DataSetImport],
      values: Map[String, String]
    ): Form[DataSetImport] =
      form.bind(form.data ++ values)

    if (!filledForm.hasErrors && filledForm.value.isDefined) {
      val dataSetImport = filledForm.value.get

      // add import file(s) param values and errors
      val extraValuesOrErrors = handleImportFiles(dataSetImport)
      val extraValues = extraValuesOrErrors.collect { case (param, Some(value)) => (param, value) }
      val extraErrors = extraValuesOrErrors.collect { case (param, None) => FormError(param, "error.required", param) }

      extraErrors.foldLeft(addToForm(filledForm, extraValues)){_.withError(_)}
    } else
      filledForm
  }

  override protected def saveCall(
    importInfo: DataSetImport)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val importWithFixedScheduledTime = importInfo.copyCore(
      importInfo._id, importInfo.timeCreated, importInfo.timeLastExecuted, importInfo.scheduled, importInfo.scheduledTime.map(fillZeroesIfNeeded)
    )

    super.saveCall(importWithFixedScheduledTime).map { id =>
      scheduleOrCancel(id, importWithFixedScheduledTime); id
    }
  }

  override protected def updateCall(
    importInfo: DataSetImport)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val importWithFixedScheduledTime = importInfo.copyCore(
      importInfo._id, importInfo.timeCreated, importInfo.timeLastExecuted, importInfo.scheduled, importInfo.scheduledTime.map(fillZeroesIfNeeded)
    )

    //TODO: remove the old files if any
    super.updateCall(importWithFixedScheduledTime).map { id =>
      scheduleOrCancel(id, importWithFixedScheduledTime); id
    }
  }

  def idAndNames = restrictAny {
    implicit request =>
      for {
        imports <- repo.find(sort = Seq(AscSort("name")))
      } yield {
        val idAndNames = imports.map(dataImport =>
          Json.obj(
            "_id" -> dataImport._id,
            "name" -> dataImport.dataSetId
          )
        )
        Ok(JsArray(idAndNames.toSeq))
      }
  }

  def copy(id: BSONObjectID) = restrictAny {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Data set import #${id.stringify} not found"))
      ) { dataSetImport =>
        val newDataSetImport = dataSetImport.copyCore(
          None, new java.util.Date(), None, dataSetImport.scheduled, dataSetImport.scheduledTime
        )

        super.saveCall(newDataSetImport).map { newId =>
          scheduleOrCancel(newId, newDataSetImport)
          Redirect(routes.DataSetImportController.get(newId)).flashing("success" -> s"Data Set import '${dataSetImport.dataSetId}' has been copied.")
        }
      }
    )
  }

  private def handleImportFiles(
    importInfo: DataSetImport)(
    implicit request: Request[AnyContent]
  ): Map[String, Option[String]] = {
    val tempId = BSONObjectID.generate().stringify

    def copyImportFile(name: String, file: File): String = {
      if (new java.io.File(importFolder).exists()) {
        val folderDelimiter = if (importFolder.endsWith("/")) "" else "/"
        val path = importFolder + folderDelimiter + tempId + "/" + name
        copyFile(file, path)
        path
      } else
        throw new AdaException(s"Data set import folder $importFolder does not exist. Create one or override the setting 'datasetimport.import.folder' in custom.conf.")
    }

    def pathKeyValue(
      fileParamKey: String,
      pathParamKey: String)(
      implicit request: Request[AnyContent]
    ): (String, Option[String]) = {
      val path: Option[String] = getFile(fileParamKey, request).map(dataFile =>
        copyImportFile(dataFile._1, dataFile._2)
      ) match {
        case Some(path) => Some(path)
        case None => getRequestParamValueOptional(pathParamKey)
      }
      (pathParamKey, path)
    }

    importInfo match {
      case _: CsvDataSetImport =>
        Seq(pathKeyValue("dataFile", "path")).toMap

      case _: JsonDataSetImport =>
        Seq(pathKeyValue("dataFile", "path")).toMap

      case _: TranSmartDataSetImport =>
        Seq(
          pathKeyValue("dataFile", "dataPath"),
          pathKeyValue("mappingFile", "mappingPath")
        ).toMap

      case _ => Map()
    }
  }

  override protected def deleteCall(
    id: BSONObjectID)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) =
    super.deleteCall(id).map { _ =>
      dataSetImportScheduler.cancel(id); ()
    }

  private def scheduleOrCancel(id: BSONObjectID, importInfo: DataSetImport): Unit = {
    if (importInfo.scheduled)
      dataSetImportScheduler.schedule(importInfo.scheduledTime.get)(id)
    else
      dataSetImportScheduler.cancel(id)
  }

  private def getFile(fileParamKey: String, request: Request[AnyContent]): Option[(String, java.io.File)] = {
    val dataFileOption = request.body.asMultipartFormData.flatMap(_.file(fileParamKey))
    dataFileOption.flatMap { dataFile =>
      if (dataFile.filename.nonEmpty)
        Some((dataFile.filename, dataFile.ref.file))
      else
        None
    }
  }

  private def copyFile(src: File, location: String): Unit = {
    val dest = new File(location)
    val destFolder = dest.getCanonicalFile.getParentFile
    if (!destFolder.exists()) {
      destFolder.mkdirs()
    }
    new FileOutputStream(dest).getChannel.transferFrom(
      new FileInputStream(src).getChannel, 0, Long.MaxValue
    )
  }
}