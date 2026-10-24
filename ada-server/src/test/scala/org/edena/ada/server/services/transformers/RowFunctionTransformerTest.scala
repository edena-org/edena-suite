package org.edena.ada.server.services.transformers

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.edena.ada.server.dataaccess.StoreTypes.{MessageStore, TranslationStore}
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.edena.ada.server.models.DataSetFormattersAndIds.fieldFormat
import org.edena.ada.server.models.datatrans.DataSetTransformation._
import org.edena.ada.server.models.datatrans._
import org.edena.ada.server.models.{Field, ScheduledTime, StorageType}
import org.edena.ada.server.services.transformers.rowfunction.{JsonRowFunction, RowFunctionFields}
import org.edena.ada.server.services.{DataSetService, DataSetServiceImpl}
import org.edena.core.field.FieldTypeId
import org.edena.core.store._
import org.edena.json.HasFormat
import org.edena.store.json.{BSONObjectIDFormat, JsObjectIdentity, JsonFieldUpdateStore}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsNull, JsObject, Json}
import reactivemongo.api.bson.BSONObjectID

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.reflect.{ClassTag, classTag}
import scala.util.Try

// a concrete row function transformation, as a real one would be defined (in models.datatrans)
case class TestRowFunctionTransformation(
  _id: Option[BSONObjectID] = None,

  sourceDataSetId: String,
  inFieldNames: Seq[String],
  outFields: Seq[Field],
  execSpec: RowFunctionExecSpec = RowFunctionExecSpec(),

  resultDataSetSpec: ResultDataSetSpec,
  streamSpec: StreamSpec = StreamSpec(),
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends RowFunctionTransformation {

  override def copyCore(
    __id: Option[BSONObjectID],
    _timeCreated: Date,
    _timeLastExecuted: Option[Date],
    _scheduled: Boolean,
    _scheduledTime: Option[ScheduledTime]
  ) = copy(
    _id = __id,
    timeCreated = _timeCreated,
    timeLastExecuted = _timeLastExecuted,
    scheduled = _scheduled,
    scheduledTime = _scheduledTime
  )
}

object TestRowFunctionTransformation extends HasFormat[TestRowFunctionTransformation] {
  val format = Json.format[TestRowFunctionTransformation]
}

// and its transformer, which only creates the function
private class TestRowFunctionTransformer(
  function: JsonRowFunction
) extends AbstractRowFunctionTransformer[TestRowFunctionTransformation] {

  val createdFunctions = new AtomicInteger()

  override protected def createFunction(spec: TestRowFunctionTransformation, fields: RowFunctionFields) =
    Future.successful {
      createdFunctions.incrementAndGet()
      function
    }
}

// runs of a row function transformation with in-memory stores, and stubs (dynamic proxies) of the other services
class RowFunctionTransformerTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("RowFunctionTransformerTest")
  private implicit val materializer: Materializer = Materializer(system)
  import system.dispatcher

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 30.seconds)
    super.afterAll()
  }

  private val dataSetId = "test.source"

  private val aField = Field("a", fieldType = FieldTypeId.Integer)
  private val bField = Field("b", fieldType = FieldTypeId.Integer)
  private val sumField = Field("sum", label = Some("Sum"), fieldType = FieldTypeId.Double)

  private def row(a: Int) = Json.obj(JsObjectIdentity.name -> BSONObjectID.generate(), "a" -> a, "b" -> 10 * a)

  private def aOf(row: JsObject) = (row \ "a").as[Int]

  private def sumOf(row: JsObject) = (row \ "sum").asOpt[Int]

  // computes the outputs of the inputs, recording the inputs and the closings
  private class TestFunction(compute: JsObject => JsObject) extends JsonRowFunction {
    val inputs = new ConcurrentLinkedQueue[JsObject]()
    val closeCount = new AtomicInteger()

    override def apply(batch: Seq[JsObject]) = Future(
      batch.map { input =>
        inputs.add(input)
        compute(input)
      }
    )

    override def close() = Future(closeCount.incrementAndGet()).map(_ => ())
  }

  // computes a + b, the output of a row whose `a` is failing has no sum
  private class SumFunction(failingAs: Set[Int] = Set()) extends TestFunction(input =>
    if (failingAs.contains(aOf(input))) Json.obj() else Json.obj("sum" -> (aOf(input) + (input \ "b").as[Int]))
  )

  // saves a derived data set in memory, other methods are unsupported
  private class SavingDataSetService {
    @volatile var saved: Option[(ResultDataSetSpec, Seq[JsObject], Seq[Field])] = None

    val service = stub[DataSetService] {
      case ("saveDerivedDataSet", Seq(_, resultSpec: ResultDataSetSpec, source: Source[_, _], fields: Traversable[_], _, _)) =>
        source.asInstanceOf[Source[JsObject, _]].runWith(Sink.seq).map { rows =>
          saved = Some((resultSpec, rows, fields.toSeq.map(_.asInstanceOf[Field])))
        }
    }
  }

  private class Fixture(
    fields: Seq[Field],
    rows: Seq[JsObject],
    function: JsonRowFunction,
    dataSetService: Option[DataSetService] = None,
    withFieldUpdates: Boolean = true
  ) {
    val fieldStore = new InMemoryStore[Field, String](fields, _.name)
    val dataSetStore = if (withFieldUpdates) new InMemoryJsonFieldUpdateStore(rows) else new InMemoryJsonStore(rows)
    val storeUpdates = new AtomicInteger()

    private val dsa = stub[DataSetAccessor] {
      case ("dataSetId", Nil) => dataSetId
      case ("fieldStore", Nil) => fieldStore
      case ("dataSetStore", Nil) => dataSetStore
      case ("updateDataSetStore", Nil) => Future(storeUpdates.incrementAndGet()).map(_ => ())
    }

    private val dsaf = stub[DataSetAccessorFactory] {
      case ("getOrError", Seq(`dataSetId`)) => Future(dsa)
    }

    private val messageStore = stub[MessageStore] {
      case ("save", Seq(_)) => Future(BSONObjectID.generate())
    }

    val transformer = new TestRowFunctionTransformer(function)
    transformer.messageRepo = messageStore
    transformer.dsaf = dsaf
    transformer.actorSystem = system
    transformer.materializer = materializer
    transformer.dataSetService = dataSetService.getOrElse(
      new DataSetServiceImpl(dsaf, stub[TranslationStore](PartialFunction.empty), messageStore)
    )

    def run(spec: TestRowFunctionTransformation): Unit = result(transformer.runAsFuture(spec))

    def runFailure(spec: TestRowFunctionTransformation): Throwable = Try(run(spec)).failed.get

    def rowsByA: Map[Int, JsObject] = dataSetStore.items.map(row => (aOf(row), row)).toMap

    def fieldUpdates: Seq[(BSONObjectID, JsObject)] =
      dataSetStore match {
        case store: InMemoryJsonFieldUpdateStore => store.fieldUpdates.asScala.toSeq
        case _ => Nil
      }

    def updateBatchSizes: Seq[Int] =
      dataSetStore match {
        case store: InMemoryJsonFieldUpdateStore => store.updateBatchSizes.asScala.toSeq
        case _ => Nil
      }
  }

  private def inPlaceSpec(
    outFields: Seq[Field] = Seq(sumField),
    execSpec: RowFunctionExecSpec = RowFunctionExecSpec()
  ) = TestRowFunctionTransformation(
    sourceDataSetId = dataSetId,
    inFieldNames = Seq("a", "b"),
    outFields = outFields,
    execSpec = execSpec,
    resultDataSetSpec = ResultDataSetSpec(dataSetId, "Ignored"),
    streamSpec = StreamSpec(batchSize = Some(2))
  )

  private val resultSpec = ResultDataSetSpec("test.result", "Result", StorageType.Mongo)

  private val skip = RowFunctionExecSpec(onRowError = RowErrorHandling.Skip)

  private def result[T](future: Future[T]) = Await.result(future, 30.seconds)

  "Row function transformation in place" should "add the out field to the dictionary and set the out values" in {
    val rows = (1 to 5).map(row)
    val function = new SumFunction()
    val fixture = new Fixture(Seq(aField, bField), rows, function)

    fixture.run(inPlaceSpec())

    fixture.dataSetStore.items.toSet shouldBe rows.map(row => row + ("sum" -> Json.toJson(11 * aOf(row)))).toSet
    fixture.fieldUpdates.map(_._2.keys) shouldBe Seq.fill(5)(Set("sum"))
    fixture.dataSetStore.updateCount.get shouldBe 0
    fixture.fieldStore.items.toSet shouldBe Set(aField, bField, sumField)
    fixture.storeUpdates.get shouldBe 1
    function.closeCount.get shouldBe 1
  }

  it should "read only the in fields, and keep the other fields as they are, also if changed in the meantime" in {
    val rows = (1 to 3).map(a => row(a) + ("note" -> Json.toJson(s"note $a")))
    var store: InMemoryJsonStore = null

    // changes a row's note when computing its sum (as another writer could)
    val function = new TestFunction(input => {
      val storedRow = store.items.find(aOf(_) == aOf(input)).get
      result(store.update(storedRow + ("note" -> Json.toJson("changed"))))
      Json.obj("sum" -> 0)
    })

    val fixture = new Fixture(Seq(aField, bField, Field("note")), rows, function)
    store = fixture.dataSetStore

    fixture.run(inPlaceSpec())

    fixture.rowsByA.view.mapValues(row => ((row \ "note").as[String], sumOf(row))).toMap shouldBe
      Map(1 -> ("changed", Some(0)), 2 -> ("changed", Some(0)), 3 -> ("changed", Some(0)))
    fixture.dataSetStore.projections.asScala.toSet shouldBe Set(Seq("a", "b", JsObjectIdentity.name))
  }

  it should "update the rows whole, as read just before, with a store without field updates" in {
    val rows = (1 to 3).map(a => row(a) + ("note" -> Json.toJson(s"note $a")))
    val fixture = new Fixture(Seq(aField, bField, Field("note")), rows, new SumFunction(), withFieldUpdates = false)

    fixture.run(inPlaceSpec())

    fixture.dataSetStore.items.toSet shouldBe rows.map(row => row + ("sum" -> Json.toJson(11 * aOf(row)))).toSet
    fixture.dataSetStore.updateCount.get shouldBe 3
  }

  it should "leave the failed rows as they are if skipped" in {
    val fixture = new Fixture(Seq(aField, bField), (1 to 4).map(row), new SumFunction(failingAs = Set(2, 3)))

    fixture.run(inPlaceSpec(execSpec = skip))

    fixture.rowsByA.view.mapValues(sumOf).toMap shouldBe Map(1 -> Some(11), 2 -> None, 3 -> None, 4 -> Some(44))
    fixture.fieldUpdates.size shouldBe 2
  }

  it should "process only the rows with an undefined out field if requested" in {
    val rows = Seq(row(1) + ("sum" -> Json.toJson(0)), row(2), row(3) + ("sum" -> JsNull))
    val function = new SumFunction()
    val fixture = new Fixture(Seq(aField, bField, sumField.copy(label = None)), rows, function)

    fixture.run(inPlaceSpec(execSpec = RowFunctionExecSpec(onlyUndefinedOutFields = true)))

    function.inputs.asScala.map(aOf).toSet shouldBe Set(2, 3)
    fixture.rowsByA.view.mapValues(sumOf).toMap shouldBe Map(1 -> Some(0), 2 -> Some(22), 3 -> Some(33))
    fixture.fieldStore.items.find(_.name == "sum").flatMap(_.label) shouldBe Some("Sum")
    // selected by the store
    fixture.dataSetStore.criteria.asScala should contain(Or(Seq(EqualsNullCriterion("sum"))))
  }

  it should "process only the rows with an undefined Json out field, checking them as read" in {
    val entitiesField = Field("entities", fieldType = FieldTypeId.Json)
    val rows = Seq(row(1) + ("entities" -> Json.obj("x" -> 1)), row(2), row(3) + ("entities" -> JsNull))
    val function = new TestFunction(input => Json.obj("entities" -> Json.obj("sum" -> (aOf(input) + (input \ "b").as[Int]))))
    val fixture = new Fixture(Seq(aField, bField, entitiesField), rows, function)

    fixture.run(inPlaceSpec(Seq(entitiesField), RowFunctionExecSpec(onlyUndefinedOutFields = true)))

    function.inputs.asScala.map(aOf).toSet shouldBe Set(2, 3)
    fixture.rowsByA.view.mapValues(row => (row \ "entities").as[JsObject]).toMap shouldBe
      Map(1 -> Json.obj("x" -> 1), 2 -> Json.obj("sum" -> 22), 3 -> Json.obj("sum" -> 33))
    // whether a Json field is undefined can't be tested by every store (Elasticsearch)
    all(fixture.dataSetStore.criteria.asScala.map(_.fieldNames)) shouldNot contain("entities")
  }

  it should "read the rows by pages (of ids)" in {
    val rows = (1 to 2100).map(row)
    val fixture = new Fixture(Seq(aField, bField), rows, new SumFunction())

    fixture.run(inPlaceSpec(execSpec = RowFunctionExecSpec(batchSize = Some(100))).copy(streamSpec = StreamSpec(batchSize = Some(100))))

    fixture.dataSetStore.items.flatMap(sumOf).size shouldBe 2100
    // 1000 + 1000 + 100 rows, and an empty page
    fixture.dataSetStore.findCount.get shouldBe 4
  }

  it should "set the out values in batches, by default of 100 rows" in {
    val fixture = new Fixture(Seq(aField, bField), (1 to 250).map(row), new SumFunction())

    fixture.run(inPlaceSpec().copy(streamSpec = StreamSpec()))

    fixture.dataSetStore.items.flatMap(sumOf).size shouldBe 250
    val batchSizes = fixture.updateBatchSizes
    batchSizes.sum shouldBe 250
    all(batchSizes) should be <= 100
    batchSizes.size should be < 10
  }

  it should "keep the rows updated before a failure, set before the run fails, and close the function" in {
    val function = new SumFunction(failingAs = Set(3))
    val fixture = new Fixture(Seq(aField, bField), (1 to 4).map(row), function)

    val error = fixture.runFailure(inPlaceSpec().copy(streamSpec = StreamSpec()))

    error.getMessage should include("no out field(s) sum")
    function.closeCount.get shouldBe 1
    // the rows are read (and computed one by one) in the order of their ids
    fixture.rowsByA.view.filterKeys(_ < 4).mapValues(sumOf).toMap shouldBe Map(1 -> Some(11), 2 -> Some(22), 3 -> None)
  }

  it should "call the function with at most its max batch size of rows" in {
    val callSizes = new ConcurrentLinkedQueue[Int]()
    val function = new SumFunction() {
      override val maxBatchSize = Some(2)

      override def apply(batch: Seq[JsObject]) = {
        callSizes.add(batch.size)
        super.apply(batch)
      }
    }
    val fixture = new Fixture(Seq(aField, bField), (1 to 5).map(row), function)

    fixture.run(inPlaceSpec(execSpec = RowFunctionExecSpec(batchSize = Some(5))))

    callSizes.asScala.toSeq.sorted shouldBe Seq(1, 2, 2)
    fixture.dataSetStore.items.flatMap(sumOf).size shouldBe 5
  }

  it should "close the function only once its running calls finished, and not call it after" in {
    val events = new ConcurrentLinkedQueue[String]()

    // the call for a = 1 fails at once, the one for a = 2 (with an invalid output to retry) takes a while
    val function = new JsonRowFunction {
      override def apply(inputs: Seq[JsObject]) = {
        val a = aOf(inputs.head)
        events.add(s"call $a")
        val delay = if (a == 2) 300.millis else 1.milli
        akka.pattern.after(delay, system.scheduler)(Future {
          events.add(s"end $a")
          Seq(Json.obj())
        })
      }

      override def close() = Future(events.add("close")).map(_ => ())
    }

    val fixture = new Fixture(Seq(aField, bField), (1 to 2).map(row), function)
    val execSpec = RowFunctionExecSpec(parallelism = Some(2), maxRetries = 1, retryBaseDelayMs = 1)

    fixture.runFailure(inPlaceSpec(execSpec = execSpec)).getMessage should include("no out field(s) sum")

    // a retry of the row with a = 2 would come after its call ended
    Thread.sleep(500)
    val eventSeq = events.asScala.toSeq
    eventSeq.last shouldBe "close"
    eventSeq.indexOf("end 2") should be < eventSeq.indexOf("close")
    eventSeq.count(_ == "call 2") shouldBe 1
  }

  it should "refuse to change the type of an existing field, before any change" in {
    val rows = Seq(row(1) + ("sum" -> Json.toJson("x")))
    val fields = Seq(aField, bField, Field("sum", fieldType = FieldTypeId.String))
    val fixture = new Fixture(fields, rows, new SumFunction())

    fixture.runFailure(inPlaceSpec()).getMessage should include("existing field is String")

    fixture.transformer.createdFunctions.get shouldBe 0
    fixture.fieldStore.items shouldBe fields
    fixture.dataSetStore.items shouldBe rows
  }

  it should "refuse invalid execution settings" in {
    val fixture = new Fixture(Seq(aField, bField), Seq(row(1)), new SumFunction())

    def error(execSpec: RowFunctionExecSpec) = fixture.runFailure(inPlaceSpec(execSpec = execSpec)).getMessage

    error(RowFunctionExecSpec(batchSize = Some(0))) should include("batch size must be positive")
    error(RowFunctionExecSpec(maxRetries = -1)) should include("max retries can't be negative")
    error(RowFunctionExecSpec(maxRowErrors = Some(1))) should include("only if failed rows are skipped")
    fixture.transformer.createdFunctions.get shouldBe 0
  }

  "Row function transformation to a new data set" should "save the rows with the out values and the extended dictionary" in {
    val rows = (1 to 3).map(row)
    val function = new SumFunction(failingAs = Set(2))
    val saving = new SavingDataSetService
    val fixture = new Fixture(Seq(aField, bField), rows, function, Some(saving.service))

    fixture.run(inPlaceSpec(execSpec = skip).copy(resultDataSetSpec = resultSpec))

    val (savedSpec, savedRows, savedFields) = saving.saved.get
    savedSpec shouldBe resultSpec
    savedFields shouldBe Seq(aField, bField, sumField)
    savedRows.map(row => (aOf(row), sumOf(row))).toMap shouldBe Map(1 -> Some(11), 2 -> None, 3 -> Some(33))
    fixture.dataSetStore.items shouldBe rows
    fixture.dataSetStore.updateCount.get shouldBe 0
    fixture.storeUpdates.get shouldBe 0
    function.closeCount.get shouldBe 1
  }

  it should "replace an existing out field in the dictionary" in {
    val existingSum = Field("sum", fieldType = FieldTypeId.Double, displayDecimalPlaces = Some(1))
    val saving = new SavingDataSetService
    val fixture = new Fixture(Seq(existingSum, aField, bField), Seq(row(1)), new SumFunction(), Some(saving.service))

    fixture.run(inPlaceSpec().copy(resultDataSetSpec = resultSpec))

    saving.saved.get._3 shouldBe Seq(existingSum.copy(label = Some("Sum")), aField, bField)
  }

  it should "refuse to process only the rows with an undefined out field" in {
    val fixture = new Fixture(Seq(aField, bField), Seq(row(1)), new SumFunction(), Some(new SavingDataSetService().service))

    val spec = inPlaceSpec(execSpec = RowFunctionExecSpec(onlyUndefinedOutFields = true)).copy(resultDataSetSpec = resultSpec)

    fixture.runFailure(spec).getMessage should include("supported only in place")
  }

  "Row function transformation JSON" should "round-trip, with default execution settings if missing" in {
    val spec = inPlaceSpec(execSpec = RowFunctionExecSpec(batchSize = Some(10), maxRetries = 2, onRowError = RowErrorHandling.Skip))
    val json = Json.toJson(spec)(TestRowFunctionTransformation.format).as[JsObject]

    json.as(TestRowFunctionTransformation.format) shouldBe spec
    (json + ("execSpec" -> Json.obj())).as(TestRowFunctionTransformation.format).execSpec shouldBe RowFunctionExecSpec()
  }

  "Row function transformation" should "not be retried as a whole on a failure, since its calls are" in {
    inPlaceSpec().retryRunOnFailure shouldBe false
  }

  // a stub of the interface, delegating to `handle` (by method name and args)
  private def stub[T: ClassTag](handle: PartialFunction[(String, Seq[AnyRef]), AnyRef]): T =
    Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(classTag[T].runtimeClass),
      new InvocationHandler {
        override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]) = {
          val nameAndArgs = (method.getName, Option(args).map(_.toSeq).getOrElse(Nil))
          nameAndArgs match {
            case ("toString", Nil) => "stub"
            case ("hashCode", Nil) => Int.box(System.identityHashCode(proxy))
            case ("equals", Seq(other)) => Boolean.box(proxy eq other)
            case _ if handle.isDefinedAt(nameAndArgs) => handle(nameAndArgs)
            case _ => throw new UnsupportedOperationException(s"Stub method '${method.getName}' not supported.")
          }
        }
      }
    ).asInstanceOf[T]
}

// an in-memory store supporting the criteria of field updates (by names)
private class InMemoryStore[E, ID](
  initItems: Seq[E],
  idOf: E => ID
) extends CrudStore[E, ID] {

  import scala.concurrent.ExecutionContext.Implicits.global

  protected val itemMap = mutable.LinkedHashMap[ID, E](initItems.map(item => (idOf(item), item)): _*)
  val updateCount = new AtomicInteger()

  def items: Seq[E] = synchronized(itemMap.values.toList)

  override def get(id: ID) = Future(synchronized(itemMap.get(id)))

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = Future(items.filter(matches(criterion, _)))

  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = throw new UnsupportedOperationException

  override def count(criterion: Criterion) = Future(items.count(matches(criterion, _)))

  override def save(entity: E) = Future {
    synchronized(itemMap.update(idOf(entity), entity))
    idOf(entity)
  }

  override def update(entity: E) = Future {
    synchronized(itemMap.update(idOf(entity), entity))
    updateCount.incrementAndGet()
    idOf(entity)
  }

  override def delete(id: ID) = Future(synchronized(itemMap.remove(id))).map(_ => ())

  override def deleteAll = Future(synchronized(itemMap.clear()))

  override def flushOps = Future.unit

  protected def matches(criterion: Criterion, item: E): Boolean =
    (criterion, item) match {
      case (NoCriterion, _) => true
      case (And(criteria), _) => criteria.forall(matches(_, item))
      case (Or(criteria), _) => criteria.exists(matches(_, item))
      case (InCriterion(_, values), field: Field) => values.contains(field.name)
      case (NotInCriterion(_, values), field: Field) => !values.contains(field.name)
      case _ => throw new UnsupportedOperationException(s"Criterion $criterion not supported.")
    }
}

// an in-memory store of rows supporting the reads of row function transformations (by pages of ids), recording them
private class InMemoryJsonStore(
  initRows: Seq[JsObject]
) extends InMemoryStore[JsObject, BSONObjectID](initRows, JsObjectIdentity.of(_).get) {

  import scala.concurrent.ExecutionContext.Implicits.global

  val criteria = new ConcurrentLinkedQueue[Criterion]()
  val projections = new ConcurrentLinkedQueue[Seq[String]]()
  val findCount = new AtomicInteger()

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = Future {
    criteria.add(criterion)
    projections.add(projection.toSeq)
    findCount.incrementAndGet()

    val sortedRows = sort match {
      case Nil => items
      case Seq(AscSort(JsObjectIdentity.name)) => items.sortBy(idOf)
      case _ => throw new UnsupportedOperationException(s"Sort $sort not supported.")
    }

    sortedRows
      .filter(matches(criterion, _))
      .take(limit.getOrElse(Int.MaxValue))
      .map(row => if (projection.isEmpty) row else JsObject(row.fields.filter { case (name, _) => projection.exists(_ == name) }))
  }

  override def count(criterion: Criterion) = {
    criteria.add(criterion)
    super.count(criterion)
  }

  override protected def matches(criterion: Criterion, row: JsObject): Boolean =
    criterion match {
      case EqualsNullCriterion(name) => row.value.get(name).forall(_ == JsNull)
      case GreaterCriterion(JsObjectIdentity.name, id: BSONObjectID) => idOf(row) > id.stringify
      case _ => super.matches(criterion, row)
    }

  private def idOf(row: JsObject) = JsObjectIdentity.of(row).get.stringify
}

// with field updates (as the Mongo and Elasticsearch stores), recording them
private class InMemoryJsonFieldUpdateStore(
  initRows: Seq[JsObject]
) extends InMemoryJsonStore(initRows) with JsonFieldUpdateStore {

  import scala.concurrent.ExecutionContext.Implicits.global

  val fieldUpdates = new ConcurrentLinkedQueue[(BSONObjectID, JsObject)]()
  val updateBatchSizes = new ConcurrentLinkedQueue[Int]()

  override def updateFields(idFieldValues: Traversable[(BSONObjectID, JsObject)]) = Future {
    if (idFieldValues.nonEmpty) updateBatchSizes.add(idFieldValues.size)
    idFieldValues.foreach { case (id, fieldValues) =>
      fieldUpdates.add((id, fieldValues))
      synchronized(itemMap.get(id).foreach(row => itemMap.update(id, row ++ fieldValues)))
    }
  }
}
