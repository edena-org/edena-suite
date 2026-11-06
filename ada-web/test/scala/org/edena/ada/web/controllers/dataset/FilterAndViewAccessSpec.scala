package org.edena.ada.web.controllers.dataset

import be.objectify.deadbolt.scala.cache.HandlerCache
import be.objectify.deadbolt.scala.models.Subject
import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltHandler, HandlerKey}
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.edena.ada.server.models.{DataView, Filter}
import org.edena.ada.web.models.security.DeadboltUser
import org.edena.ada.server.models.User
import org.edena.core.store.CrudStore
import org.edena.play.security.SecurityRole
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers}
import reactivemongo.api.bson.BSONObjectID

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * A data set user may see the public filters and views and their own, and change only their own,
 * whatever the route: the list (with its JSON), a JSON save (keyMode keep or replace), the filter's
 * Ajax save and a view's copy. See [[OwnerAccess]].
 */
class FilterAndViewAccessSpec extends AnyFlatSpec with Matchers with ScalaFutures {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(5.seconds)

  private def user(name: String, roles: Seq[String] = Nil) =
    DeadboltUser(User(
      _id = Some(BSONObjectID.generate()), userId = name, name = name, email = s"$name@example.org",
      roles = roles, permissions = Seq("DS:test")
    ))

  private val alice = user("alice")
  private val bob = user("bob")
  private val admin = user("admin", Seq(SecurityRole.admin))

  // A's filters: private and public; B's: private
  private val alicePrivateFilter = Filter(Some(BSONObjectID.generate()), Some("alice-private"), isPrivate = true, createdById = alice.id)
  private val alicePublicFilter = Filter(Some(BSONObjectID.generate()), Some("alice-public"), createdById = alice.id)
  private val bobPrivateFilter = Filter(Some(BSONObjectID.generate()), Some("bob-private"), isPrivate = true, createdById = bob.id)

  private def view(name: String, isPrivate: Boolean, owner: Option[DeadboltUser], default: Boolean = false) =
    DataView(Some(BSONObjectID.generate()), name, Nil, Nil, widgetSpecs = Nil, default = default, isPrivate = isPrivate, createdById = owner.flatMap(_.id))

  private val alicePrivateView = view("alice-private", isPrivate = true, Some(alice))
  private val alicePublicView = view("alice-public", isPrivate = false, Some(alice))
  // as an import creates it: no owner
  private val mainView = view("Main", isPrivate = false, None, default = true)

  // an in-memory store whose save is an upsert (as the Ignite cache stores' put)
  private def store[E](
    items: TrieMap[BSONObjectID, E])(
    idOf: E => Option[BSONObjectID],
    withId: (E, BSONObjectID) => E
  ): CrudStore[E, BSONObjectID] =
    stub[CrudStore[E, BSONObjectID]](classOf[CrudStore[_, _]]) {
      case ("get", Array(id: BSONObjectID)) => Future.successful(items.get(id))
      case ("exists", Array(id: BSONObjectID)) => Future.successful(items.contains(id))
      case ("save", Array(_: Iterable[_])) => throw new UnsupportedOperationException("save of many")
      case ("save", Array(item)) =>
        val entity = item.asInstanceOf[E]
        val id = idOf(entity).getOrElse(BSONObjectID.generate())
        items.put(id, withId(entity, id))
        Future.successful(id)
      case ("update", Array(item)) if !item.isInstanceOf[Iterable[_]] =>
        val entity = item.asInstanceOf[E]
        val id = idOf(entity).get
        if (items.contains(id)) {
          items.put(id, entity)
          Future.successful(id)
        } else
          Future.failed(new IllegalArgumentException(s"No item $id."))
      case ("find", Array(_, _, _, _, _)) => Future.successful(items.values.toSeq)
      case ("count", Array(_)) => Future.successful(items.size)
    }

  private def stub[T](clazz: Class[_])(calls: PartialFunction[(String, Array[AnyRef]), Any]): T =
    Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(clazz),
      new InvocationHandler {
        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
          val call = (method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
          calls.applyOrElse(call, (_: (String, Array[AnyRef])) =>
            throw new UnsupportedOperationException(s"Stub method '${method.getName}' not supported.")
          ).asInstanceOf[AnyRef]
        }
      }
    ).asInstanceOf[T]

  private class Fixture(currentUser: DeadboltUser) {
    val filters = TrieMap(Seq(alicePrivateFilter, alicePublicFilter, bobPrivateFilter).map(f => f._id.get -> f): _*)
    val views = TrieMap(Seq(alicePrivateView, alicePublicView, mainView).map(v => v._id.get -> v): _*)

    private val dsa = {
      val filterStore = store(filters)(_._id, (f, id) => f.copy(_id = Some(id)))
      val viewStore = store(views)(_._id, (v, id) => v.copy(_id = Some(id)))

      stub[DataSetAccessor](classOf[DataSetAccessor]) {
        case ("filterStore", _) => filterStore
        case ("dataViewStore", _) => viewStore
      }
    }

    private val dsaf = stub[DataSetAccessorFactory](classOf[DataSetAccessorFactory]) {
      case ("applySync", Array("test")) => Some(dsa)
    }

    private val handlers = new HandlerCache {
      private val handler = new DeadboltHandler {
        override def beforeAuthCheck[A](request: Request[A]) = Future.successful(None)
        override def getSubject[A](request: AuthenticatedRequest[A]): Future[Option[Subject]] = Future.successful(Some(currentUser))
        override def onAuthFailure[A](request: AuthenticatedRequest[A]) = Future.successful(Results.Unauthorized)
        override def getDynamicResourceHandler[A](request: Request[A]) = Future.successful(None)
      }

      override def apply(): DeadboltHandler = handler
      override def apply(key: HandlerKey): DeadboltHandler = handler
    }

    private val components = Helpers.stubControllerComponents()

    val filterController = new FilterControllerImpl("test", dsaf, null, null, components) {
      handlerCache = handlers

      def listed: Future[Option[Filter => Boolean]] =
        listedItemFilter(new AuthenticatedRequest(FakeRequest().withBody(AnyContentAsEmpty: AnyContent), None))
    }

    val viewController = new DataViewControllerImpl("test", dsaf, null, null, components) {
      handlerCache = handlers

      def listed: Future[Option[DataView => Boolean]] =
        listedItemFilter(new AuthenticatedRequest(FakeRequest().withBody(AnyContentAsEmpty: AnyContent), None))
    }

    def call(action: Action[AnyContent], request: Request[AnyContent]): Result =
      action(request).futureValue

    def saveFilterJson(json: JsValue, keyMode: Option[String]): Result =
      call(filterController.save, jsonRequest("/dataSets/filters/save", json, keyMode))

    def saveViewJson(json: JsValue, keyMode: Option[String]): Result =
      call(viewController.save, jsonRequest("/dataSets/dataviews/save", json, keyMode))

    private def jsonRequest(path: String, json: JsValue, keyMode: Option[String]): Request[AnyContent] =
      FakeRequest("POST", path + "?dataSet=test" + keyMode.fold("")("&keyMode=" + _))
        .withBody(AnyContentAsJson(json): AnyContent)
  }

  "A JSON save (keep)" should "refuse an id that exists, and change nothing" in {
    val fixture = new Fixture(bob)
    import fixture._

    val json = Json.toJson(alicePrivateFilter.copy(name = Some("by-bob"), isPrivate = false))

    saveFilterJson(json, None).header.status shouldBe 400
    filters(alicePrivateFilter._id.get) shouldBe alicePrivateFilter
  }

  it should "save an item with a new id" in {
    val fixture = new Fixture(bob)
    import fixture._

    val newId = BSONObjectID.generate()
    saveFilterJson(Json.toJson(Filter(Some(newId), Some("bob-new"))), None).header.status shouldBe 201
    filters(newId).createdById shouldBe bob.id
  }

  "A JSON save (replace)" should "refuse to change another user's filter or view" in {
    val fixture = new Fixture(bob)
    import fixture._

    saveFilterJson(Json.toJson(alicePublicFilter.copy(name = Some("by-bob"))), Some("replace")).header.status shouldBe 400
    filters(alicePublicFilter._id.get) shouldBe alicePublicFilter

    saveViewJson(Json.toJson(alicePublicView.copy(name = "by-bob")), Some("replace")).header.status shouldBe 400
    views(alicePublicView._id.get) shouldBe alicePublicView
  }

  it should "refuse a non-admin to change a view without an owner" in {
    val fixture = new Fixture(bob)
    import fixture._

    saveViewJson(Json.toJson(mainView.copy(name = "by-bob")), Some("replace")).header.status shouldBe 400
    views(mainView._id.get) shouldBe mainView
  }

  it should "change the owner's own filter, and an admin any view" in {
    val bobsFixture = new Fixture(bob)
    bobsFixture.saveFilterJson(Json.toJson(bobPrivateFilter.copy(name = Some("renamed"))), Some("replace")).header.status shouldBe 201
    bobsFixture.filters(bobPrivateFilter._id.get).name shouldBe Some("renamed")
    bobsFixture.filters(bobPrivateFilter._id.get).createdById shouldBe bob.id

    val adminsFixture = new Fixture(admin)
    adminsFixture.saveViewJson(Json.toJson(mainView.copy(name = "renamed")), Some("replace")).header.status shouldBe 201
    adminsFixture.views(mainView._id.get).name shouldBe "renamed"
  }

  "The filter's Ajax save" should "create a filter, even if given an existing id" in {
    val fixture = new Fixture(bob)
    import fixture._

    val result = call(
      filterController.saveAjax(alicePrivateFilter.copy(name = Some("by-bob"))),
      FakeRequest("POST", "/dataSets/filters/saveAjax?dataSet=test").withBody(AnyContentAsEmpty: AnyContent)
    )

    result.header.status shouldBe 200
    filters(alicePrivateFilter._id.get) shouldBe alicePrivateFilter
    filters.values.filter(_.name.contains("by-bob")).map(_.createdById).toSeq shouldBe Seq(bob.id)
  }

  "A view's copy" should "not find another user's private view" in {
    val fixture = new Fixture(bob)
    import fixture._

    val result = call(viewController.copy(alicePrivateView._id.get), FakeRequest("POST", "/").withBody(AnyContentAsEmpty: AnyContent))

    result.header.status shouldBe 404
    views.size shouldBe 3
  }

  it should "copy a public view as the user's own" in {
    val fixture = new Fixture(bob)
    import fixture._

    val result = call(viewController.copy(alicePublicView._id.get), FakeRequest("POST", "/").withBody(AnyContentAsEmpty: AnyContent))

    result.header.status shouldBe 303
    views.values.filter(_.name == "alice-public copy").map(_.createdById).toSeq shouldBe Seq(bob.id)
  }

  "The list" should "show a data set user the public filters and views and their own" in {
    val fixture = new Fixture(bob)
    import fixture._

    val filterListed = filterController.listed.futureValue.get
    filters.values.filter(filterListed).flatMap(_.name).toSet shouldBe Set("alice-public", "bob-private")

    val viewListed = viewController.listed.futureValue.get
    views.values.filter(viewListed).map(_.name).toSet shouldBe Set("alice-public", "Main")
  }

  it should "show an admin all of them" in {
    val fixture = new Fixture(admin)
    import fixture._

    filterController.listed.futureValue shouldBe None
    viewController.listed.futureValue shouldBe None
  }
}
