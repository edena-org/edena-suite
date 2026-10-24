package org.edena.ada.server.services.transformers.rowfunction

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models.Field
import org.edena.ada.server.models.datatrans.{RowErrorHandling, RowFunctionExecSpec}
import org.edena.core.field.FieldTypeId
import org.edena.store.json.BSONObjectIDFormat
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsNull, JsObject, Json}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.Try

class RowFunctionFlowTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("RowFunctionFlowTest")
  import system.dispatcher

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 30.seconds)
    super.afterAll()
  }

  private val codec = new RowFunctionCodec(
    RowFunctionFields(
      Seq(Field("a", fieldType = FieldTypeId.Integer), Field("b", fieldType = FieldTypeId.Integer)),
      Seq(Field("sum", fieldType = FieldTypeId.Double))
    ),
    FieldTypeHelper.fieldTypeFactory()
  )

  private def row(a: Int) = Json.obj("_id" -> BSONObjectID.generate(), "a" -> a, "b" -> 10 * a, "other" -> "x")

  private def sum(input: JsObject) = Json.obj("sum" -> ((input \ "a").as[Int] + (input \ "b").as[Int]))

  private val skip = RowFunctionExecSpec(onRowError = RowErrorHandling.Skip)

  // a function recording its calls (inputs), whose output for a call (and its number) is given by `f`
  private class TestFunction(f: (Seq[JsObject], Int) => Future[Seq[JsObject]]) extends JsonRowFunction {
    val calls = new ConcurrentLinkedQueue[Seq[JsObject]]()

    override def apply(inputs: Seq[JsObject]) = {
      calls.add(inputs)
      f(inputs, calls.size)
    }

    def callSizes = calls.asScala.toSeq.map(_.size)
  }

  private def summing = new TestFunction((inputs, _) => Future(inputs.map(sum)))

  private def run(
    rows: Seq[JsObject],
    function: JsonRowFunction,
    spec: RowFunctionExecSpec
  ): Future[Seq[RowOutcome]] =
    Source(rows).via(RowFunctionFlow(function, codec, spec)).runWith(Sink.seq)

  private def result[T](future: Future[T]) = Await.result(future, 30.seconds)

  private def failures(outcomes: Seq[RowOutcome]) =
    outcomes.collect { case RowOutcome.Failed(row, error) => ((row \ "a").as[Int], error) }

  "Row function flow" should "compute the out values of the rows, passing only the in fields" in {
    val rows = (1 to 5).map(row)
    val function = summing

    val outcomes = result(run(rows, function, RowFunctionExecSpec()))

    outcomes.map(_.row).toSet shouldBe rows.toSet
    val computed = outcomes.collect { case outcome: RowOutcome.Computed => outcome }
    computed.map(_.outValues).toSet shouldBe rows.map(row => Json.obj("sum" -> 11 * (row \ "a").as[Int])).toSet
    computed.map(_.updatedRow).toSet shouldBe rows.map(row => row + ("sum" -> Json.toJson(11 * (row \ "a").as[Int]))).toSet
    function.calls.asScala.flatten.map(_.keys).toSet shouldBe Set(Set("a", "b"))
  }

  it should "pass an undefined in value as null" in {
    val function = new TestFunction((inputs, _) => Future(inputs.map(_ => Json.obj("sum" -> 0))))

    result(run(Seq(Json.obj("a" -> 1)), function, RowFunctionExecSpec()))

    function.calls.asScala.toSeq shouldBe Seq(Seq(Json.obj("a" -> 1, "b" -> JsNull)))
  }

  it should "call the function with batches" in {
    val function = summing

    result(run((1 to 7).map(row), function, RowFunctionExecSpec(batchSize = Some(3))))

    function.callSizes.sorted shouldBe Seq(1, 3, 3)
  }

  it should "run at most the given number of calls at the same time" in {
    val running = new AtomicInteger()
    val maxRunning = new AtomicInteger()

    val function = new TestFunction((inputs, _) => {
      maxRunning.accumulateAndGet(running.incrementAndGet(), (x, y) => math.max(x, y))
      akka.pattern.after(50.millis, system.scheduler)(Future {
        running.decrementAndGet()
        inputs.map(sum)
      })
    })

    val outcomes = result(run((1 to 12).map(row), function, RowFunctionExecSpec(parallelism = Some(3))))

    outcomes.size shouldBe 12
    maxRunning.get shouldBe 3
  }

  it should "retry only the rows whose output was invalid" in {
    val function = new TestFunction((inputs, callNum) => Future(
      inputs.map(input => if (callNum == 1 && (input \ "a").as[Int] == 2) Json.obj() else sum(input))
    ))

    val outcomes = result(run((1 to 4).map(row), function, RowFunctionExecSpec(batchSize = Some(4), maxRetries = 1, retryBaseDelayMs = 1)))

    all(outcomes) shouldBe a[RowOutcome.Computed]
    function.callSizes shouldBe Seq(4, 1)
    (function.calls.asScala.last.head \ "a").as[Int] shouldBe 2
  }

  it should "fail a row still invalid after the retries" in {
    val function = new TestFunction((inputs, _) => Future(
      inputs.map(input => if ((input \ "a").as[Int] == 2) Json.obj("total" -> 1) else sum(input))
    ))

    val outcomes = result(run((1 to 3).map(row), function, skip.copy(batchSize = Some(3), maxRetries = 2, retryBaseDelayMs = 1)))

    val Seq((failedA, error)) = failures(outcomes)
    failedA shouldBe 2
    error shouldBe a[RowFunctionException]
    error.getMessage should include("no out field(s) sum")
    function.callSizes shouldBe Seq(3, 1, 1)
  }

  it should "retry a failed call" in {
    val function = new TestFunction((inputs, callNum) =>
      if (callNum == 1) Future.failed(new IllegalStateException("Service unavailable")) else Future(inputs.map(sum))
    )

    val outcomes = result(run((1 to 2).map(row), function, RowFunctionExecSpec(batchSize = Some(2), maxRetries = 1, retryBaseDelayMs = 1)))

    all(outcomes) shouldBe a[RowOutcome.Computed]
    function.callSizes shouldBe Seq(2, 2)
  }

  it should "not retry an error the function deems non-retryable" in {
    val function = new TestFunction((_, _) => Future.failed(new IllegalArgumentException("Bad request"))) {
      override def isRetryable(error: Throwable) = !error.isInstanceOf[IllegalArgumentException]
    }

    val outcomes = result(run(Seq(row(1)), function, skip.copy(maxRetries = 3, retryBaseDelayMs = 1)))

    failures(outcomes).map(_._2.getMessage) shouldBe Seq("Bad request")
    function.callSizes shouldBe Seq(1)
  }

  it should "handle an exception thrown by the function as a failed call" in {
    val function = new TestFunction((_, _) => throw new IllegalStateException("Thrown"))

    val outcomes = result(run(Seq(row(1), row(2)), function, skip))

    failures(outcomes).map(_._2.getMessage) shouldBe Seq("Thrown", "Thrown")
  }

  it should "time out a call, and retry it" in {
    val function = new TestFunction((inputs, callNum) =>
      if (callNum == 1) Promise[Seq[JsObject]]().future else Future(inputs.map(sum))
    )

    val timeoutSpec = RowFunctionExecSpec(timeoutMs = Some(100), maxRetries = 1, retryBaseDelayMs = 1)
    all(result(run(Seq(row(1)), function, timeoutSpec))) shouldBe a[RowOutcome.Computed]

    val neverResponding = new TestFunction((_, _) => Promise[Seq[JsObject]]().future)
    val outcomes = result(run(Seq(row(1)), neverResponding, skip.copy(timeoutMs = Some(100))))
    failures(outcomes).map(_._2.getMessage) shouldBe Seq("The function didn't respond within 100 ms.")
  }

  it should "fail all the rows of a call returning another number of outputs" in {
    val function = new TestFunction((inputs, _) => Future(inputs.tail.map(sum)))

    val outcomes = result(run((1 to 3).map(row), function, skip.copy(batchSize = Some(3))))

    failures(outcomes).map(_._2.getMessage) shouldBe Seq.fill(3)("The function returned 2 outputs for 3 rows.")
  }

  it should "fail the stream on a failed row by default" in {
    val failedRow = row(2)
    val function = new TestFunction((inputs, _) => Future(
      inputs.map(input => if ((input \ "a").as[Int] == 2) Json.obj() else sum(input))
    ))

    val error = Try(result(run(Seq(row(1), failedRow, row(3)), function, RowFunctionExecSpec()))).failed.get

    error shouldBe a[RowFunctionException]
    error.getMessage should include(s"for the row '${(failedRow \ "_id").as[BSONObjectID].stringify}'")
    error.getMessage should include("no out field(s) sum")
  }

  it should "skip failed rows, failing the stream once more than the max failed" in {
    val function = new TestFunction((inputs, _) => Future(
      inputs.map(input => if ((input \ "a").as[Int] % 2 == 0) Json.obj() else sum(input))
    ))
    val rows = (1 to 6).map(row)

    val outcomes = result(run(rows, function, skip.copy(maxRowErrors = Some(3))))
    failures(outcomes).map(_._1).sorted shouldBe Seq(2, 4, 6)
    outcomes.collect { case RowOutcome.Computed(_, outValues) => (outValues \ "sum").as[Int] }.sorted shouldBe Seq(11, 33, 55)

    val error = Try(result(run(rows, function, skip.copy(maxRowErrors = Some(2))))).failed.get
    error.getMessage should include("failed for more than 2 rows")
  }

  it should "pass by the rows with all the out values if only undefined ones are computed" in {
    val computedRows = Seq(row(1) + ("sum" -> Json.toJson(0)), row(4) + ("sum" -> Json.toJson(-1)))
    val rows = Seq(computedRows(0), row(2), row(3) + ("sum" -> JsNull), computedRows(1), row(5))
    val function = summing

    val outcomes = result(
      run(rows, function, RowFunctionExecSpec(batchSize = Some(2), maxCallsPerMinute = Some(600), onlyUndefinedOutFields = true))
    )

    outcomes.collect { case RowOutcome.Unchanged(row) => row }.toSet shouldBe computedRows.toSet
    outcomes.collect { case RowOutcome.Computed(row, outValues) => ((row \ "a").as[Int], (outValues \ "sum").as[Int]) }.toSet shouldBe
      Set((2, 22), (3, 33), (5, 55))
    // only the rows to compute are batched (and rate limited)
    function.callSizes.sorted shouldBe Seq(1, 2)
  }

  it should "limit the calls per minute, retries included, spacing them evenly" in {
    val callNanos = new ConcurrentLinkedQueue[Long]()
    val function = new TestFunction((inputs, callNum) => {
      callNanos.add(System.nanoTime())
      if (callNum == 1) Future.failed(new IllegalStateException("Too many requests")) else Future(inputs.map(sum))
    })

    // a call per 100 ms, although 3 could run at the same time
    val spec = RowFunctionExecSpec(parallelism = Some(3), maxCallsPerMinute = Some(600), maxRetries = 1, retryBaseDelayMs = 1)
    val outcomes = result(run((1 to 3).map(row), function, spec))

    all(outcomes) shouldBe a[RowOutcome.Computed]
    val starts = callNanos.asScala.toSeq.sorted
    starts.size shouldBe 4
    starts.zip(starts.tail).map { case (start, nextStart) => (nextStart - start).nanos.toMillis }.min should be >= 70L
  }

  it should "call the function with at most its max batch size of rows" in {
    val function = new TestFunction((inputs, _) => Future(inputs.map(sum))) {
      override val maxBatchSize = Some(2)
    }

    result(run((1 to 5).map(row), function, RowFunctionExecSpec(batchSize = Some(4))))

    function.callSizes.sorted shouldBe Seq(1, 2, 2)
  }

  it should "call a function of a row (sync or async) for each row by itself, retrying only the failed one" in {
    val calledAs = new ConcurrentLinkedQueue[Int]()

    // fails the first time for a = 2
    def compute(input: JsObject) = {
      val a = (input \ "a").as[Int]
      calledAs.add(a)
      if (a == 2 && calledAs.asScala.count(_ == 2) == 1) throw new IllegalStateException("Flaky") else sum(input)
    }

    val functions = Seq(JsonRowFunction.sync(compute), JsonRowFunction.async(input => Future(compute(input))))

    functions.foreach { function =>
      calledAs.clear()
      val outcomes = result(run((1 to 3).map(row), function, RowFunctionExecSpec(batchSize = Some(3), maxRetries = 1, retryBaseDelayMs = 1)))

      all(outcomes) shouldBe a[RowOutcome.Computed]
      calledAs.asScala.toSeq.sorted shouldBe Seq(1, 2, 2, 3)
    }
  }
}
