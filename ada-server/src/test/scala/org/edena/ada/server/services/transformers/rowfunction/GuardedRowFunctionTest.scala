package org.edena.ada.server.services.transformers.rowfunction

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Try

class GuardedRowFunctionTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("GuardedRowFunctionTest")
  import system.dispatcher

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 30.seconds)
    super.afterAll()
  }

  // a function whose calls complete only with `response`, counting its calls and closings
  private class TestFunction(response: Future[Seq[JsObject]]) extends JsonRowFunction {
    val callCount = new AtomicInteger()
    @volatile var closeCount = 0
    @volatile var closedWithCallCount = -1

    override def apply(inputs: Seq[JsObject]) = {
      callCount.incrementAndGet()
      response
    }

    override def isRetryable(error: Throwable) = !error.isInstanceOf[IllegalArgumentException]

    override def close() = Future {
      closeCount += 1
      closedWithCallCount = callCount.get
    }
  }

  private def result[T](future: Future[T]) = Await.result(future, 30.seconds)

  private val input = Seq(Json.obj("a" -> 1))

  "Guarded row function" should "be closed only once its running call finished" in {
    val response = Promise[Seq[JsObject]]()
    val function = new TestFunction(response.future)
    val guarded = new GuardedRowFunction(function, 10.seconds)(system.scheduler, system.dispatcher)

    val call = guarded(input)
    val closing = guarded.close()

    Thread.sleep(200)
    function.closeCount shouldBe 0

    response.success(Seq(Json.obj("b" -> 2)))
    result(call) shouldBe Seq(Json.obj("b" -> 2))
    result(closing)
    function.closeCount shouldBe 1
  }

  it should "fail a call after the closing without calling the function, and not retry it" in {
    val function = new TestFunction(Future.successful(Nil))
    val guarded = new GuardedRowFunction(function, 10.seconds)(system.scheduler, system.dispatcher)

    result(guarded.close())
    val error = Try(result(guarded(input))).failed.get

    error.getMessage should include("closed")
    guarded.isRetryable(error) shouldBe false
    function.callCount.get shouldBe 0
    function.closeCount shouldBe 1
  }

  it should "close the function after the max wait even if a call still runs" in {
    val function = new TestFunction(Promise[Seq[JsObject]]().future)
    val guarded = new GuardedRowFunction(function, 200.millis)(system.scheduler, system.dispatcher)

    guarded(input)
    result(guarded.close())

    function.closeCount shouldBe 1
    function.closedWithCallCount shouldBe 1
  }

  it should "count a function throwing an exception as a finished call, and keep its retryability" in {
    val function = new TestFunction(Future.successful(Nil)) {
      override def apply(inputs: Seq[JsObject]) = throw new IllegalArgumentException("Bad input")
    }
    val guarded = new GuardedRowFunction(function, 10.seconds)(system.scheduler, system.dispatcher)

    val error = Try(result(guarded(input))).failed.get
    error.getMessage shouldBe "Bad input"
    guarded.isRetryable(error) shouldBe false
    guarded.isRetryable(new IllegalStateException) shouldBe true

    // no call is running, so the closing doesn't wait
    val start = System.currentTimeMillis
    result(guarded.close())
    (System.currentTimeMillis - start) should be < 5000L
  }
}
