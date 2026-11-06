package org.edena.play.controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.Timeout
import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.edena.core.FilterCondition
import org.edena.core.store.ReadonlyStore
import org.edena.play.Page
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers}
import play.twirl.api.Html

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * A controller's [[ReadonlyControllerImpl.listedItemFilter]] restricts what find, listAll and their
 * JSONL export list: the pages, the total and the export have only the listed items.
 */
class ListedItemFilterSpec extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(5.seconds)

  private implicit val system: ActorSystem = ActorSystem("ListedItemFilterSpec")
  private implicit val materializer: Materializer = Materializer(system)
  private implicit val timeout: Timeout = Timeout(5.seconds)

  override def afterAll(): Unit = system.terminate()

  private val items = (1 to 7).map(i => ListedItem(s"item$i", owner = if (i % 2 == 0) "bob" else "alice"))
  private val bobs = items.filter(_.owner == "bob").map(_._id)

  private def controller(itemFilter: Option[ListedItem => Boolean]) =
    new ListedItemController(Helpers.stubControllerComponents(), items, itemFilter)

  private def jsonRequest(path: String) =
    FakeRequest("GET", path).withHeaders("Accept" -> "application/json").withBody(AnyContentAsEmpty: AnyContent)

  private def ids(result: Result) =
    (Json.parse(Helpers.contentAsString(Future.successful(result))) \\ "_id").map(_.as[String])

  "find" should "page through all the items with no listed-item filter" in {
    val c = controller(None)

    ids(c.find(0, "", Nil)(jsonRequest("/")).futureValue) shouldBe items.take(3).map(_._id)
    c.lastPage.get.total shouldBe 7
  }

  it should "page through only the listed items, with their total" in {
    val c = controller(Some(_.owner == "bob"))

    ids(c.find(0, "", Nil)(jsonRequest("/")).futureValue) shouldBe bobs.take(3)
    c.lastPage.get.total shouldBe 3

    ids(c.find(1, "", Nil)(jsonRequest("/")).futureValue) shouldBe Nil
  }

  "listAll" should "list only the listed items" in {
    val c = controller(Some(_.owner == "bob"))

    ids(c.listAll("")(jsonRequest("/")).futureValue) shouldBe bobs
    c.lastPage.get.total shouldBe 3
  }

  "The JSONL export" should "have only the listed items" in {
    val c = controller(Some(_.owner == "bob"))

    val result = c.find(0, "", Nil)(jsonRequest("/?format=jsonl")).futureValue
    val lines = Helpers.contentAsString(Future.successful(result)).split("\n").toSeq

    lines.map(line => (Json.parse(line) \ "_id").as[String]) shouldBe bobs
  }

  it should "be refused over the cap, counting only the listed items" in {
    val c = controller(Some(_.owner == "bob"))
    c.maxCount = 2

    c.find(0, "", Nil)(jsonRequest("/?format=jsonl")).futureValue.header.status shouldBe 400

    c.maxCount = 3
    c.find(0, "", Nil)(jsonRequest("/?format=jsonl")).futureValue.header.status shouldBe 200
  }
}

case class ListedItem(_id: String, owner: String)

object ListedItem {
  implicit val format: Format[ListedItem] = Json.format[ListedItem]
}

// a minimal controller over an in-memory store (paged by find's limit and skip)
class ListedItemController(
  val controllerComponents: ControllerComponents,
  items: Seq[ListedItem],
  itemFilter: Option[ListedItem => Boolean]
) extends ReadonlyControllerImpl[ListedItem, String] {

  @volatile var lastPage: Option[Page[ListedItem]] = None
  @volatile var maxCount = 10

  override protected val pageLimit = 3

  override protected val store: ReadonlyStore[ListedItem, String] =
    Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(classOf[ReadonlyStore[_, _]]),
      new InvocationHandler {
        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
          (method.getName, Option(args).getOrElse(Array.empty[AnyRef])) match {
            case ("find", Array(_, _, _, limit: Option[_], skip: Option[_])) =>
              val skipped = items.drop(skip.map(_.asInstanceOf[Int]).getOrElse(0))
              Future.successful(limit.map(_.asInstanceOf[Int]).fold(skipped)(skipped.take))
            // find's default arguments (projection, limit, skip)
            case ("find$default$3", _) => Nil
            case ("find$default$4" | "find$default$5", _) => None
            case ("count", _) => Future.successful(items.size)
            case (name, _) => throw new UnsupportedOperationException(s"Stub method '$name' not supported.")
          }
      }
    ).asInstanceOf[ReadonlyStore[ListedItem, String]]

  override protected def homeCall = Call("GET", "/")

  override protected def jsonlExportMaxCount: Option[Int] = Some(maxCount)

  override protected def listedItemFilter(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[Option[ListedItem => Boolean]] = Future.successful(itemFilter)

  override protected type ListViewData = Page[ListedItem]

  override protected def getListViewData(
    page: Page[ListedItem],
    conditions: Seq[FilterCondition]
  ) = { _ =>
    lastPage = Some(page)
    Future.successful(page)
  }

  override protected def listView = { _ => page => Html(page.items.map(_._id).mkString(",")) }

  override protected type ShowViewData = ListedItem

  override protected def getShowViewData(id: String, item: ListedItem) = { _ => Future.successful(item) }

  override protected def showView = { _ => item => Html(item._id) }
}
