package org.edena.ada.web.controllers

import be.objectify.deadbolt.scala._
import be.objectify.deadbolt.scala.cache.{DefaultPatternCache, HandlerCache}
import be.objectify.deadbolt.scala.models.Subject
import org.edena.ada.server.dataaccess.StoreTypes.RunnableSpecStore
import org.edena.ada.server.models.User
import org.edena.ada.web.models.security.DeadboltUser
import org.edena.core.store.CrudStore
import org.edena.play.security.SecurityRole
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers}
import reactivemongo.api.bson.BSONObjectID

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

/**
 * A runnable spec (which may hold the credentials of its input) is shown to admins only: its get
 * is restricted as the controller's other actions.
 */
class RunnableGetAccessSpec extends AnyFlatSpec with Matchers with ScalaFutures {

  private def user(roles: Seq[String]) =
    DeadboltUser(User(_id = Some(BSONObjectID.generate()), userId = "bob", name = "Bob", email = "bob@example.org", roles = roles))

  private val denied = Results.Redirect("/denied")

  private class Fixture(subject: Option[DeadboltUser]) {
    val loads = new AtomicInteger()

    // no spec found (counting the loads)
    private val runnableSpecStore = Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(classOf[CrudStore[_, _]]),
      new InvocationHandler {
        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
          case "get" =>
            loads.incrementAndGet()
            Future.successful(None)
          case name => throw new UnsupportedOperationException(s"Stub method '$name' not supported.")
        }
      }
    ).asInstanceOf[RunnableSpecStore]

    private val handlers = new HandlerCache {
      private val handler = new DeadboltHandler {
        override def beforeAuthCheck[A](request: Request[A]) = Future.successful(None)
        override def getSubject[A](request: AuthenticatedRequest[A]): Future[Option[Subject]] = Future.successful(subject)
        override def onAuthFailure[A](request: AuthenticatedRequest[A]) = Future.successful(denied)
        override def getDynamicResourceHandler[A](request: Request[A]) = Future.successful(None)
      }

      override def apply(): DeadboltHandler = handler
      override def apply(key: HandlerKey): DeadboltHandler = handler
    }

    private val ecProvider = new ExecutionContextProvider {
      override def get(): ExecutionContext = ExecutionContext.global
    }
    private val analyzer = new StaticConstraintAnalyzer(new DefaultPatternCache)
    private val components = Helpers.stubControllerComponents()
    private val deadboltActions =
      new DeadboltActions(analyzer, handlers, ecProvider, new ConstraintLogic(analyzer, ecProvider), components.parsers)

    val controller = new RunnableController(
      null, null, runnableSpecStore, null, null, Configuration.empty, null, null, components
    ) {
      handlerCache = handlers
      deadbolt = deadboltActions
    }

    def get: Result =
      controller.get(BSONObjectID.generate())(FakeRequest().withBody(AnyContentAsEmpty: AnyContent)).futureValue
  }

  "A runnable spec's get" should "be refused to a non-admin, without loading the spec" in {
    val fixture = new Fixture(Some(user(Nil)))

    fixture.get.header.headers.get("Location") shouldBe Some("/denied")
    fixture.loads.get shouldBe 0
  }

  it should "be refused to no user" in {
    val fixture = new Fixture(None)

    fixture.get.header.headers.get("Location") shouldBe Some("/denied")
    fixture.loads.get shouldBe 0
  }

  it should "load the spec for an admin" in {
    val fixture = new Fixture(Some(user(Seq(SecurityRole.admin))))

    fixture.get.header.status shouldBe 404
    fixture.loads.get shouldBe 1
  }
}
