package org.edena.play.controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.edena.core.store.ReadonlyStore
import play.api.http.HeaderNames.CONTENT_DISPOSITION
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

  // -- JSONL export (triggered on find/listAll via the `format=jsonl` query param) --

  // Field-injected (like the BaseController resources) so no subclass constructor changes; kept
  // non-implicit under distinctive names to avoid clashing with subclasses' own implicit
  // system/materializer (e.g. those injected for ExportableAction).
  @Inject protected var jsonlExportActorSystem: ActorSystem = _
  @Inject protected var jsonlExportMaterializer: Materializer = _

  /** Query param requesting an alternative response format (currently only `jsonl`). */
  protected val exportFormatParamName = "format"

  protected val jsonlMimeType = "application/x-ndjson"

  /**
   * Kill-switch for the generic JSONL export riding on find/listAll. Disable (override to false)
   * for controllers whose bulk download must be gated by its own permission — e.g. record-level
   * data set browsing, where the dedicated export action carries a separate permission and the
   * list action must not quietly grant a full dump.
   */
  protected def jsonlExportEnabled: Boolean = true

  /**
   * Refuse a JSONL export when more than this many items match, so a single list URL can never
   * trigger an unbounded collection scan. Read from config `export.jsonl.maxCount` (default
   * 10000); override to `None` for no cap.
   */
  protected def jsonlExportMaxCount: Option[Int] =
    Some(configuration.getOptional[Int]("export.jsonl.maxCount").getOrElse(10000))

  protected def isJsonlRequested(implicit request: Request[_]): Boolean =
    request.getQueryString(exportFormatParamName).contains("jsonl")

  protected def jsonlFileName: String =
    entityName.toLowerCase.replaceAll("\\W+", "_") + "s.jsonl"

  /**
   * Streamed newline-delimited JSON attachment of all the items matching the filter. Backed by
   * `store.findAsStream`, so stores with native streaming support never hold the full result set
   * in memory. Projected to [[listViewColumns]] (when defined) so the export exposes exactly the
   * columns the list view does. Guarded by [[jsonlExportEnabled]] and the [[jsonlExportMaxCount]]
   * pre-count check.
   */
  protected def jsonlExportResult(
    orderBy: String,
    conditions: Seq[FilterCondition] = Nil
  ): Future[Result] =
    if (!jsonlExportEnabled)
      Future.successful(Forbidden(s"JSONL export is not enabled for $entityName."))
    else
      toCriterion(conditions).flatMap { criterion =>
        def stream: Future[Result] =
          store.findAsStream(criterion, toSort(orderBy), listViewColumns.getOrElse(Nil))(
            jsonlExportActorSystem, jsonlExportMaterializer
          ).map(jsonlAttachment)

        jsonlExportMaxCount.map(max =>
          store.count(criterion).flatMap(count =>
            if (count > max)
              Future.successful(jsonlExportRefused(count, max))
            else
              stream
          )
        ).getOrElse(stream)
      }

  // the JSONL export of the items listed through listedItemFilter (all loaded, see there)
  private def filteredJsonlExportResult(
    orderBy: String,
    conditions: Seq[FilterCondition],
    itemFilter: E => Boolean
  ): Future[Result] =
    if (!jsonlExportEnabled)
      Future.successful(Forbidden(s"JSONL export is not enabled for $entityName."))
    else
      filteredItemsAndCount(None, orderBy, conditions, itemFilter, None).map { case (items, count) =>
        jsonlExportMaxCount.filter(count > _) match {
          case Some(max) => jsonlExportRefused(count, max)
          case None => jsonlAttachment(Source(items.toList))
        }
      }

  private def jsonlAttachment(source: Source[E, _]): Result = {
    val lines = source.map(item => Json.stringify(toJson(item)) + "\n")
    Ok.chunked(lines)
      .as(jsonlMimeType)
      .withHeaders(CONTENT_DISPOSITION -> s"attachment; filename=$jsonlFileName")
  }

  private def jsonlExportRefused(count: Int, max: Int): Result =
    BadRequest(
      s"JSONL export refused: $count items match, exceeding the cap of $max. " +
        "Narrow the filter, or raise 'export.jsonl.maxCount'."
    )

  /**
   * Restricts the items the request's user may see in [[find]], [[listAll]] and their JSONL
   * export, e.g. to hide other users' private items. `None`, the default, lists every item. With a
   * predicate, all the items matching the filter are loaded whole (with no [[listViewColumns]]
   * projection) and the predicate is applied before the paging, so restrict only small
   * collections this way.
   */
  protected def listedItemFilter(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[Option[E => Boolean]] = Future.successful(None)

  private def filteredItemsAndCount(
    page: Option[Int],
    orderBy: String,
    conditions: Seq[FilterCondition],
    itemFilter: E => Boolean,
    limit: Option[Int]
  ): Future[(Traversable[E], Int)] =
    for {
      criterion <- toCriterion(conditions)
      items <- store.find(criterion = criterion, sort = toSort(orderBy))
    } yield {
      val listedItems = items.filter(itemFilter).toSeq
      val skip = (page, limit) match {
        case (Some(page), Some(limit)) => page * limit
        case _ => 0
      }

      (limit.fold(listedItems.drop(skip))(limit => listedItems.slice(skip, skip + limit)), listedItems.size)
    }

  /**
    * Display the paginated list.
    *
    * When the `format=jsonl` query param is present, ALL items matching the filter (not just the
    * current page) are exported as a JSONL attachment instead.
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
    listedItemFilter.flatMap { itemFilter =>
      if (isJsonlRequested)
        itemFilter.fold(jsonlExportResult(orderBy, conditions))(filteredJsonlExportResult(orderBy, conditions, _))
      else
        for {
          (items, count) <- itemFilter.fold(getFutureItemsAndCount(page, orderBy, conditions))(
            filteredItemsAndCount(Some(page), orderBy, conditions, _, Some(pageLimit))
          )

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
   * When the `format=jsonl` query param is present, all items are exported as a JSONL attachment
   * instead.
   *
   * @param orderBy Column to be sorted
   */
  def listAll(orderBy: String) = AuthAction { implicit request =>
    listedItemFilter.flatMap { itemFilter =>
      if (isJsonlRequested)
        itemFilter.fold(jsonlExportResult(orderBy))(filteredJsonlExportResult(orderBy, Nil, _))
      else
        for {
          (items, count) <- itemFilter.fold(getFutureItemsAndCount(None, orderBy, Nil, Nil, None))(
            filteredItemsAndCount(None, orderBy, Nil, _, None)
          )

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