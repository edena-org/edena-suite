package org.edena.ada.web.security

import org.edena.ada.server.models.User
import org.edena.ada.server.services.UserManager
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.cache.SyncCacheApi
import play.api.http.SecretConfiguration
import play.api.libs.crypto.{CookieSigner, DefaultCookieSigner}
import play.api.mvc.{Request, Results}
import play.api.test.FakeRequest
import play.api.{Environment, Mode}

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

class LockedUserSpec extends AnyFlatSpec with Matchers with ScalaFutures {

  private val alice = User(userId = "alice", name = "Alice", email = "alice@example.org")

  // the user store: only findById is used
  private class Fixture {
    val users = TrieMap(alice.userId -> alice)

    private val userManager = Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(classOf[UserManager]),
      new InvocationHandler {
        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
          case "findById" => Future.successful(users.get(args(0).asInstanceOf[String]))
          case name => throw new UnsupportedOperationException(s"Stub method '$name' not supported.")
        }
      }
    ).asInstanceOf[UserManager]

    private val sessionCache = new SyncCacheApi {
      private val values = TrieMap[String, Any]()

      override def set(key: String, value: Any, expiration: Duration): Unit = values.put(key, value)
      override def remove(key: String): Unit = values.remove(key)
      override def getOrElseUpdate[A: ClassTag](key: String, expiration: Duration)(orElse: => A): A =
        values.getOrElseUpdate(key, orElse).asInstanceOf[A]
      override def get[T: ClassTag](key: String): Option[T] = values.get(key).map(_.asInstanceOf[T])
    }

    val authConfig = new AdaAuthConfig {
      override val ldapUserManager: UserManager = userManager
      override val cache: SyncCacheApi = sessionCache
      override val cookieSigner: CookieSigner = new DefaultCookieSigner(SecretConfiguration("a-test-secret-of-at-least-32-bytes"))
      override val environment: Environment = Environment.simple(mode = Mode.Test)
    }

    // a request with the auth cookie of a session of the user (as a login starts it)
    def requestOfSession(userId: String): Request[_] = {
      implicit val loginRequest = FakeRequest()
      val token = authConfig.idContainer.startNewSession(userId, authConfig.sessionTimeoutInSeconds).futureValue
      val cookies = authConfig.tokenAccessor.put(token)(Results.Ok).newCookies
      FakeRequest().withCookies(cookies: _*)
    }

    def lock(userId: String) = users.updateWith(userId)(_.map(_.copy(locked = true)))
  }

  "A user" should "be resolved if not locked" in {
    val fixture = new Fixture

    fixture.authConfig.resolveUser("alice").futureValue shouldBe Some(alice)
    fixture.authConfig.resolveUser("bob").futureValue shouldBe None
  }

  it should "not be resolved if locked" in {
    val fixture = new Fixture
    fixture.lock("alice")

    fixture.authConfig.resolveUser("alice").futureValue shouldBe None
  }

  "The user of a session" should "lose access once locked, at the next request" in {
    val fixture = new Fixture
    val request = fixture.requestOfSession("alice")

    fixture.authConfig.currentUser(request).futureValue shouldBe Some(alice)

    fixture.lock("alice")
    fixture.authConfig.currentUser(request).futureValue shouldBe None
  }
}
