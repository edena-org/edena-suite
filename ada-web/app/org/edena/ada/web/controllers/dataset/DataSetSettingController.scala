package org.edena.ada.web.controllers.dataset

import java.util.concurrent.TimeoutException

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.edena.ada.web.controllers._
import org.edena.ada.web.controllers.core.AdaCrudControllerImpl
import org.edena.ada.server.dataaccess.StoreTypes.{DataSetSettingStore}
import org.edena.ada.server.models.{ChartType, DataSetFormattersAndIds, DataSetSetting, FilterShowFieldStyle, StorageType, WidgetSpec}
import org.edena.ada.server.models.DataSetFormattersAndIds.{DataSetSettingIdentity, serializableDataSetSettingFormat, widgetSpecFormat}
import org.edena.ada.server.models.NavigationItem.navigationItemFormat
import org.edena.ada.server.models._
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.data.{Form, FormError, Mapping}
import play.api.data.Forms._
import play.api.mvc._
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.services.DataSetService
import org.edena.ada.web.services.DataSpaceService
import views.html.{category, datasetsetting => view}
import org.edena.ada.web.controllers.dataset.routes.{DataSetSettingController => dataSetSettingRoutes}
import org.edena.core.store.Criterion.Infix
import org.edena.play.controllers._
import org.edena.play.formatters._
import play.api.data.format.Formatter
import play.api.libs.json.Json

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class DataSetSettingController @Inject() (
  repo: DataSetSettingStore,
  dataSpaceService: DataSpaceService,
  dataSetService: DataSetService,
  dsaf: DataSetAccessorFactory,
  val controllerComponents: ControllerComponents
  ) extends AdaCrudControllerImpl[DataSetSetting, BSONObjectID](repo)

    with AdminRestrictedCrudController[BSONObjectID]
    with HasBasicFormCreateView[DataSetSetting]
    with HasFormShowEqualEditView[DataSetSetting, BSONObjectID]
    with HasBasicListView[DataSetSetting] {

  private implicit val chartTypeFormatter = EnumFormatter(ChartType)

  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val filterShowFieldStyleFormatter = EnumFormatter(FilterShowFieldStyle)
  private implicit val storageTypeFormatter = EnumFormatter(StorageType)
  private implicit val widgetSpecFormatter = JsonFormatter[WidgetSpec]
  private implicit val bsonObjectIdFormatter = BSONObjectIDStringFormatter
  private implicit val navigationItemFormatter = JsonFormatter[NavigationItem]

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "keyFieldName" -> nonEmptyText,
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
      "ownerId" -> optional(of[BSONObjectID]),
      "showSideCategoricalTree" -> boolean,
      "extraNavigationItems" -> seq(of[NavigationItem]).transform(mergeMenus, mergeMenus),
      "customControllerClassName" -> optional(text),
      "description" -> optional(text),
      "widgetEngineClassName" -> optional(text),
      "customStorageCollectionName" -> optional(text)
    )(DataSetSetting.apply)(DataSetSetting.unapply)
  )

  override protected val homeCall = routes.DataSetSettingController.find()

  // create view

  override protected def createView = { implicit ctx =>
    throw new IllegalArgumentException("Create function not available for data set setting.")
  }

  // edit view and data (show view = edit view)

  override protected type EditViewData = (
    BSONObjectID,
    Form[DataSetSetting]
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[DataSetSetting]
  ) = { _ =>
    val settingFuture = form.value match {
      case Some(setting) => Future(Some(setting))
      case None => repo.get(id)
    }

    for {
      // get the setting if not provided
      setting <- settingFuture
    } yield {
      val newForm = form.copy(value = setting)
      (id, newForm)
    }
  }

  override protected def editView = { implicit ctx => data: (BSONObjectID, Form[DataSetSetting]) =>
    val (id, form) = data
    form.value.map(_.dataSetId) match {
      case Some(dataSetId) =>
        implicit val context = DataSetWebContext(dataSetId)

        view.editNormal(id, form)

      case None => throw new IllegalArgumentException(s"No data set setting found for an id ${id.stringify}.")
    }
  }

  // list view

  override protected def listView = { implicit ctx =>
    (view.list(_, _)).tupled
  }

  def editForDataSet(dataSet: String) = restrictAdminAny(noCaching = true) { implicit request =>
    val foundSettingFuture = repo.find("dataSetId" #== dataSet).map(_.headOption)
    foundSettingFuture.map { setting =>
      setting.fold(
        NotFound(s"Setting for the data set '#$dataSet' not found")
      ) { entity =>
        implicit val msg = messagesApi.preferred(request)

        render {
          case Accepts.Html() => {
            implicit val context = DataSetWebContext(dataSet)
            val updateCall = dataSetSettingRoutes.updateForDataSet(entity._id.get)
            val cancelCall = context.dataSetRouter.getDefaultView(Nil)

            Ok(view.edit(
              fillForm(entity),
              updateCall,
              cancelCall,
              result(dataSpaceService.getTreeForCurrentUser(request)))
            )
          }
          case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the edit process")
        InternalServerError(t.getMessage)
    }
  }

  def updateForDataSet(id: BSONObjectID) = restrictAdminAny(noCaching = true) { implicit request =>
    val dataSetIdFuture = repo.get(id).map(_.get.dataSetId)
    dataSetIdFuture.flatMap { dataSetId =>
      update(
        id,
        _ => Redirect(new DataSetRouter(dataSetId).getDefaultView(Nil))
      ).apply(request)
    }
  }

  override protected def updateCall(
    item: DataSetSetting)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      id <- repo.update(item)

      // data set accessor
      dsa <- dsaf.getOrError(item.dataSetId)

      // update the data set repo since we've changed the setting, which could affect how the data set is accessed
      _ <- dsa.updateDataSetStore(item)
    } yield
      id

  private def mergeMenus(navigationItems: Seq[NavigationItem]): Seq[NavigationItem] =
    navigationItems.foldLeft(ArrayBuffer[NavigationItem]()) { case (items, navItem) =>
      navItem match {
        // if it's a link we just add it
        case link: Link => items :+ link

        // menu with not header take the links and add
        case Menu("", links) => items ++ links

        // for normal menus we need to search if we don't have it already with the same header
        case Menu(header, links) if header.nonEmpty => items.zipWithIndex.find { case (navItem,_ ) =>
          navItem match {
            case Menu(header2, _) => header == header2
            case _ => false
          }}.map { case (matchedItem, index) =>
          // if yes we add the links and update
          val matchedMenu = matchedItem.asInstanceOf[Menu]
          val updatedMenu = matchedMenu.copy(links = matchedMenu.links ++ links)
          items(index) = updatedMenu
          items
        }.getOrElse(
          // otherwise we add the entire menu to the list
          items :+ Menu(header, links)
        )
      }
    }
}