package org.edena.play.controllers

import org.edena.core.store.ReadonlyStore
import play.api.libs.json._
import play.api.mvc._
import org.edena.core.FilterCondition
import org.edena.play.Page
import org.edena.play.security.HasAuthAction
import org.edena.core.store._
import org.edena.play.util.WebUtil.toSort
import org.edena.play.controllers.BaseController
import org.webjars.play.WebJarsUtil
import play.api.Configuration
import play.api.i18n.{Lang, MessagesApi}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import org.edena.core.DefaultTypes.Seq

/**
  * Trait defining a controller with basic read (find/get) operations, i.e., no data alternation is allowed.
  *
  * @author Peter Banda
  */
trait ReadonlyController[ID] {

  def get(id: ID): Action[AnyContent]

  def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent]

  def listAll(orderBy: String): Action[AnyContent]
}

/**
 * Standard implementation of a readonly controller using an asynchronous readonly repo to access the data.
 *
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 *
 * @author Peter Banda
 */
abstract class ReadonlyControllerImpl[E: Format, ID] extends BaseController
  with ReadonlyController[ID]
  with HasListView[E]
  with HasShowView[E, ID]
  with HasAuthAction
  with ExceptionHandler {

  protected val pageLimit = 20

  protected implicit val executionContext: ExecutionContext = defaultExecutionContext

  protected val timeout = 200.seconds

  protected def homeCall: Call

  protected def goHome = Redirect(homeCall)

  protected def store: ReadonlyStore[E, ID]

  protected def toJson(item: E) = Json.toJson(item)

  protected def toJson(items: Traversable[E]) = Json.toJson(items)

  protected def listViewColumns: Option[Seq[String]] = None

  protected def entityNameKey = ""
  protected lazy val entityName = if (entityNameKey.isEmpty) "Item" else messagesApi.apply(entityNameKey + ".displayName")(Lang.defaultLang)

  protected def formatId(id: ID) = id.toString

  /**
   * Retrieve single object by its Id.
   * NotFound response is generated if key does not exists.
   *
   * @param id id/ primary key of the object.
   */
  def get(id: ID) = AuthAction { implicit request =>
    {
      for {
        // retrieve the item
        item <- store.get(id)

        // create a view data if the item has been found
        viewData <- item.fold(
          Future(Option.empty[ShowViewData])
        ) { entity =>
          getShowViewData(id, entity)(request).map(Some(_))
        }
      } yield
        item match {
          case None => NotFound(s"$entityName #${formatId(id)} not found")
          case Some(entity) =>
            implicit val req = request: Request[_]

//            implicit val webcontext = webContext(request)

            render {
              case Accepts.Html() => Ok(showViewWithContext(viewData.get))
              case Accepts.Json() => Ok(toJson(entity))
            }
        }
    }.recover(handleGetExceptions(id))
  }

  /**
    * Display the paginated list.
    *
    * @param page Current page number (starts from 0)
    * @param orderBy Column to be sorted
    * @param conditions Filter applied on items
    */
  def find(
    page: Int,
    orderBy: String,
    conditions: Seq[FilterCondition]
  ) = AuthAction { implicit request =>
    {
      for {
        (items, count) <- getFutureItemsAndCount(page, orderBy, conditions)

        viewData <- getListViewData(
          Page(items, page, page * pageLimit, count, orderBy),
          conditions
        )(request)
      } yield {
        implicit val req = request: Request[_]
        render {
          case Accepts.Html() => Ok(listViewWithContext(viewData))
          case Accepts.Json() => Ok(toJson(items))
        }
      }
    }.recover(handleFindExceptions)
  }

  /**
   * Display all items in a paginated fashion.
   *
   * @param orderBy Column to be sorted
   */
  def listAll(orderBy: String) = AuthAction { implicit request =>
    {
      for {
        (items, count) <- getFutureItemsAndCount(None, orderBy, Nil, Nil, None)

        viewData <- getListViewData(
          Page(items, 0, 0, count, orderBy),
          Nil
        )(request)
      } yield {
        implicit val req = request: Request[_]

        render {
          case Accepts.Html() => Ok(listViewWithContext(viewData))
          case Accepts.Json() => Ok(toJson(items))
        }
      }
    }.recover(handleListAllExceptions)
  }

  protected def getFutureItemsAndCount(
    page: Int,
    orderBy: String,
    filter: Seq[FilterCondition]
  ): Future[(Traversable[E], Int)] =
    getFutureItemsAndCount(Some(page), orderBy, filter, listViewColumns.getOrElse(Nil), Some(pageLimit))

  protected def getFutureItemsAndCount(
    page: Option[Int],
    orderBy: String,
    filter: Seq[FilterCondition],
    projection: Seq[String],
    limit: Option[Int]
  ): Future[(Traversable[E], Int)] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    for {
      criterion <- toCriterion(filter)

      itemsCount <- {
        val itemsFuture = store.find(criterion, sort, projection, limit, skip)
        val countFuture = store.count(criterion)

        for { items <- itemsFuture; count <- countFuture} yield
          (items, count)
      }
    } yield
      itemsCount
  }

  protected def getFutureItems(
    page: Option[Int],
    orderBy: String,
    filter: Seq[FilterCondition],
    projection: Seq[String],
    limit: Option[Int]
  ): Future[Traversable[E]] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    toCriterion(filter).flatMap (criterion =>
      store.find(criterion, sort, projection, limit, skip)
    )
  }

  protected def getFutureItemsForCriteria(
    page: Option[Int],
    orderBy: String,
    criterion: Criterion,
    projection: Seq[String],
    limit: Option[Int]
  ): Future[Traversable[E]] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    store.find(criterion, sort, projection, limit, skip)
  }

  protected def getFutureCount(filter: Seq[FilterCondition]): Future[Int] =
    toCriterion(filter).flatMap(store.count)

  protected def toCriterion(
    filter: Seq[FilterCondition]
  ): Future[Criterion] = {
    val fieldNames = filter.seq.map(_.fieldName)

    filterValueConverters(fieldNames).map(
      FilterCondition.toCriteria(_, filter)
    )
  }

  protected def filterValueConverters(
    fieldNames: Traversable[String]
  ): Future[Map[String, String => Option[Any]]] =
    Future(Map())

  protected def result[T](future: Future[T]): T =
    Await.result(future, timeout)

  protected def handleGetExceptions(id: ID)(implicit request: Request[_]) = handleExceptionsWithId("a get", id)
  protected def handleFindExceptions(implicit request: Request[_]) = handleExceptions("a find")
  protected def handleListAllExceptions(implicit request: Request[_]) = handleExceptions("a list-all")

  protected def handleExceptionsWithId(
    functionName: String,
    id: ID)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {
    val idPart = s" for the item with id '${formatId(id)}'"
    handleExceptions(functionName, Some(idPart))
  }
}