package org.edena.ada.web.security

import be.objectify.deadbolt.scala.cache.{DefaultPatternCache, HandlerCache}
import be.objectify.deadbolt.scala.models.Subject
import be.objectify.deadbolt.scala._
import org.edena.ada.server.models.User
import org.edena.ada.web.controllers.dataset.ControllerName
import org.edena.ada.web.models.security.{DataSetPermission, DeadboltUser}
import org.edena.play.security.ActionSecurity.AuthenticatedActionAny
import org.edena.play.security.{ActionSecurity, DeadboltHandlerKeys, SecurityRole}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers}
import reactivemongo.api.bson.BSONObjectID

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

/**
 * The chained restrictions of [[ActionSecurity]] (as the filters' and views' owner-checked routes
 * use them) run the action once, and only when the whole chain authorizes the request.
 */
class ActionSecurityChainSpec extends AnyFlatSpec with Matchers with ScalaFutures {

  private val permission = DataSetPermission("test", ControllerName.filter, "delete")

  private def user(roles: Seq[String] = Nil, permissions: Seq[String] = Nil) =
    DeadboltUser(User(
      _id = Some(BSONObjectID.generate()), userId = "bob", name = "Bob", email = "bob@example.org",
      roles = roles, permissions = permissions
    ))

  private val dataSetUser = user(permissions = Seq("DS:test"))
  private val otherDataSetUser = user(permissions = Seq("DS:other"))
  private val admin = user(roles = Seq(SecurityRole.admin))

  private val denied = Results.Redirect("/denied")
  private val done = Results.Redirect("/done")

  // as AdaDeadboltHandler: the default handler redirects on a failure, the 'unauthorizedStatus' one answers 401
  private class Fixture(subject: Option[DeadboltUser]) {
    private def handler(failure: Result) = new DeadboltHandler {
      override def beforeAuthCheck[A](request: Request[A]) = Future.successful(None)
      override def getSubject[A](request: AuthenticatedRequest[A]): Future[Option[Subject]] = Future.successful(subject)
      override def onAuthFailure[A](request: AuthenticatedRequest[A]) = Future.successful(failure)
      override def getDynamicResourceHandler[A](request: Request[A]) = Future.successful(None)
    }

    private val handlers = new HandlerCache {
      private val default = handler(denied)
      private val unauthorized = handler(Results.Unauthorized)

      override def apply(): DeadboltHandler = default
      override def apply(key: HandlerKey): DeadboltHandler =
        if (key == DeadboltHandlerKeys.unauthorizedStatus) unauthorized else default
    }

    private val ecProvider = new ExecutionContextProvider {
      override def get(): ExecutionContext = ExecutionContext.global
    }
    private val analyzer = new StaticConstraintAnalyzer(new DefaultPatternCache)
    private val components = Helpers.stubControllerComponents()

    val controller = new ChainController(
      components,
      handlers,
      new DeadboltActions(analyzer, handlers, ecProvider, new ConstraintLogic(analyzer, ecProvider), components.parsers)
    )

    val runs = new AtomicInteger()

    def action(result: Result): AuthenticatedActionAny = { _ =>
      runs.incrementAndGet()
      Future.successful(result)
    }

    def call(action: Action[AnyContent]): Result = action(FakeRequest("POST", "/")).futureValue
  }

  "Admin OR (permission AND owner)" should "not run the action for a data set user who isn't the owner" in {
    val fixture = new Fixture(Some(dataSetUser))
    import fixture._

    call(controller.adminOrPermissionAndOwner(permission, isOwner = false)(action(done))) shouldBe denied
    runs.get shouldBe 0
  }

  it should "run the action once for the owner" in {
    val fixture = new Fixture(Some(dataSetUser))
    import fixture._

    call(controller.adminOrPermissionAndOwner(permission, isOwner = true)(action(done))) shouldBe done
    runs.get shouldBe 1
  }

  it should "run the action once for an admin" in {
    val fixture = new Fixture(Some(admin))
    import fixture._

    call(controller.adminOrPermissionAndOwner(permission, isOwner = false)(action(done))) shouldBe done
    runs.get shouldBe 1
  }

  it should "not run the action for an owner without the permission, nor for no user" in {
    Seq(Some(otherDataSetUser), None).foreach { subject =>
      val fixture = new Fixture(subject)
      import fixture._

      call(controller.adminOrPermissionAndOwner(permission, isOwner = true)(action(done))) shouldBe denied
      runs.get shouldBe 0
    }
  }

  "Admin OR custom" should "run the action once, even if it answers 401" in {
    val fixture = new Fixture(Some(admin))
    import fixture._

    call(controller.adminOrCustom(isAllowed = true)(action(Results.Unauthorized))).header.status shouldBe 401
    runs.get shouldBe 1
  }

  it should "not run the action if neither restriction allows it" in {
    val fixture = new Fixture(Some(dataSetUser))
    import fixture._

    call(controller.adminOrCustom(isAllowed = false)(action(done))) shouldBe denied
    runs.get shouldBe 0
  }
}

// exposes the (protected) chained restrictions
class ChainController(
  val controllerComponents: ControllerComponents,
  handlers: HandlerCache,
  deadboltActions: DeadboltActions
) extends BaseController with ActionSecurity {

  override type USER = DeadboltUser

  handlerCache = handlers
  deadbolt = deadboltActions

  def adminOrPermissionAndOwner(
    permission: String,
    isOwner: Boolean
  ): ActionSecurity.AuthActionTransformationAny =
    restrictAdminOrPermissionAndUserCustomAny(permission, (_, _) => Future.successful(isOwner))

  def adminOrCustom(
    isAllowed: Boolean
  ): ActionSecurity.AuthActionTransformationAny =
    restrictAdminOrUserCustomAny((_, _) => Future.successful(isAllowed))
}
