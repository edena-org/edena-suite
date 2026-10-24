package org.edena.ada.server.services.transformers

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.StoreTypes.MessageStore
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.edena.ada.server.models.datatrans.DataSetTransformation._
import org.edena.ada.server.models.datatrans._
import org.edena.ada.server.models.{Category, Field, Message}
import org.edena.ada.server.services.DataSetService
import org.edena.ada.server.services.transformers.groupby.GroupByValues
import org.edena.core.field.FieldTypeId
import org.edena.core.store._
import org.edena.core.{ConditionType, FilterCondition}
import org.edena.store.json.{BSONObjectIDFormat, JsObjectIdentity}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsNull, JsObject, Json}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.reflect.{ClassTag, classTag}
import scala.util.Try

// runs of a group-by transformation with in-memory stores, and stubs (dynamic proxies) of the other services
class GroupByTransformerTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("GroupByTransformerTest")
  private implicit val materializer: Materializer = Materializer(system)
  import system.dispatcher

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 30.seconds)
    super.afterAll()
  }

  // initialized first (as in the app), since it looks up the formats of all the transformations, incl. the group-by's,
  // whose initialization needs it (initializing them the other way round, as another suite can, would deadlock)
  dataSetMetaTransformationFormat

  private val sourceId = "test.examinations"
  private val resultId = "test.patients"

  private val testsCategory = Category(Some(BSONObjectID.generate()), "Tests")

  private val patientIdField = Field("PATIENT_ID", Some("Patient Id"))
  private val visitIdField = Field("VISIT_ID")
  private val xrayField = Field("tests.xray", categoryId = testsCategory._id)
  private val noteField = Field("NOTE")

  private def row(patientId: String, visitId: String, xray: String) =
    Json.obj(
      JsObjectIdentity.name -> BSONObjectID.generate(),
      "PATIENT_ID" -> patientId, "VISIT_ID" -> visitId, "tests.xray" -> xray, "NOTE" -> s"$patientId $visitId"
    )

  // the patients' examinations interleaved
  private val rows = Seq(
    row("P2", "V3", "Ordered"),
    row("P1", "V1", "Ordered"),
    row("P2", "V3", "Not ordered"),
    row("P1", "V1", "Ordered"),
    row("P1", "V2", "Not ordered")
  )

  private val aggregations = Seq(
    GroupByAggregation("examinations", GroupByFunction.Count, label = Some("Examinations")),
    GroupByAggregation("visits", GroupByFunction.CountDistinct, Seq("VISIT_ID")),
    GroupByAggregation(
      "xray_visits", GroupByFunction.CountDistinct, Seq("VISIT_ID"), categoryName = Some("Tests"),
      conditions = Seq(FilterCondition("tests.xray", None, ConditionType.Equals, Some("Ordered"), None))
    ),
    GroupByAggregation("xray", GroupByFunction.CollectDistinct, Seq("tests.xray"), categoryName = Some("Counts"))
  )

  private def spec(
    aggregations: Seq[GroupByAggregation] = aggregations,
    resultDataSetSpec: ResultDataSetSpec = ResultDataSetSpec(resultId, "Patients")
  ) = GroupByTransformation(
    sourceDataSetId = sourceId,
    groupByFieldNames = Seq("PATIENT_ID"),
    orderBy = Seq(GroupByOrder("VISIT_ID", desc = true)),
    aggregations = aggregations,
    resultDataSetSpec = resultDataSetSpec
  )

  private def result[T](future: Future[T]) = Await.result(future, 30.seconds)

  private class Fixture {
    val dataSetStore = new SortingJsonStore(rows)

    private val sourceDsa = stub[DataSetAccessor] {
      case ("dataSetId", Nil) => sourceId
      case ("fieldStore", Nil) => new InMemoryStore[Field, String](Seq(patientIdField, visitIdField, xrayField, noteField), _.name)
      case ("categoryStore", Nil) => new CategoryStore(Seq(testsCategory))
      case ("dataSetStore", Nil) => dataSetStore
    }

    // the result data set, as saved by the data set service (with the source categories copied)
    val resultFieldStore = new InMemoryStore[Field, String](Nil, _.name)
    val resultCategoryStore = new CategoryStore(Nil)
    val resultRows = new ConcurrentLinkedQueue[JsObject]()
    val saveCalls = new ConcurrentLinkedQueue[Seq[AnyRef]]()

    private val resultDsa = stub[DataSetAccessor] {
      case ("fieldStore", Nil) => resultFieldStore
      case ("categoryStore", Nil) => resultCategoryStore
    }

    private val dsaf = stub[DataSetAccessorFactory] {
      case ("getOrError", Seq(`sourceId`)) => Future(sourceDsa)
      case ("getOrError", Seq(`resultId`)) => Future(resultDsa)
    }

    private val dataSetService = stub[DataSetService] {
      case ("saveDerivedDataSet", args @ Seq(_, _, source: Source[_, _], fields: Traversable[_], _, _)) =>
        saveCalls.add(args)
        source.asInstanceOf[Source[JsObject, _]].runWith(Sink.seq).flatMap { savedRows =>
          resultRows.addAll(savedRows.asJava)
          resultFieldStore.save(fields.map(_.asInstanceOf[Field]))
        }.map(_ => ())
    }

    val messages = new ConcurrentLinkedQueue[String]()

    private val messageStore = stub[MessageStore] {
      case ("save", Seq(message: Message)) =>
        messages.add(message.content)
        Future(BSONObjectID.generate())
    }

    val transformer = new GroupByTransformer
    transformer.messageRepo = messageStore
    transformer.dsaf = dsaf
    transformer.dataSetService = dataSetService
    transformer.actorSystem = system
    transformer.materializer = materializer

    def run(spec: GroupByTransformation): Unit = result(transformer.runAsFuture(spec))

    def runFailure(spec: GroupByTransformation): Throwable = Try(run(spec)).failed.get

    def resultRowsByPatientId: Map[String, JsObject] =
      resultRows.asScala.toSeq.map(row => ((row \ "PATIENT_ID").as[String], row - JsObjectIdentity.name)).toMap
  }

  "Group by transformation" should "save a row per group with the aggregated values" in {
    val fixture = new Fixture

    fixture.run(spec())

    fixture.resultRowsByPatientId shouldBe Map(
      "P1" -> Json.obj("PATIENT_ID" -> "P1", "examinations" -> 3, "visits" -> 2, "xray_visits" -> 1, "xray" -> Seq("Not ordered", "Ordered")),
      "P2" -> Json.obj("PATIENT_ID" -> "P2", "examinations" -> 2, "visits" -> 1, "xray_visits" -> 1, "xray" -> Seq("Ordered", "Not ordered"))
    )
    // each with a new id
    fixture.resultRows.asScala.flatMap(JsObjectIdentity.of(_)).toSet.size shouldBe 2

    fixture.messages.asScala.toSeq shouldBe Seq(s"Grouped 5 rows of '$sourceId' into 2 rows of '$resultId'.")
  }

  it should "read the rows sorted by the group-by fields and the id, only the fields needed" in {
    val fixture = new Fixture

    fixture.run(spec())

    fixture.dataSetStore.sorts.asScala.toSeq shouldBe Seq(Seq(AscSort("PATIENT_ID"), AscSort(JsObjectIdentity.name)))
    fixture.dataSetStore.projections.asScala.toSeq shouldBe Seq(Seq("PATIENT_ID", "VISIT_ID", "tests.xray"))
  }

  it should "save the group-by fields as they are and the out fields, without the source views and filters" in {
    val fixture = new Fixture

    fixture.run(spec())

    val Seq(_, resultSpec, _, fields, _, saveViewsAndFilters) = fixture.saveCalls.asScala.head
    resultSpec shouldBe ResultDataSetSpec(resultId, "Patients")
    saveViewsAndFilters shouldBe false
    // the source category (its id replaced by the data set service)
    fields.asInstanceOf[Traversable[Field]].toSeq shouldBe Seq(
      patientIdField,
      Field("examinations", Some("Examinations"), FieldTypeId.Integer),
      Field("visits", None, FieldTypeId.Integer),
      Field("xray_visits", None, FieldTypeId.Integer, categoryId = testsCategory._id),
      Field("xray", None, FieldTypeId.String, isArray = true)
    )
  }

  it should "create the categories not in the source" in {
    val fixture = new Fixture

    fixture.run(spec())

    val Seq(countsCategory) = fixture.resultCategoryStore.items
    countsCategory.name shouldBe "Counts"
    fixture.resultFieldStore.items.find(_.name == "xray").flatMap(_.categoryId) shouldBe countsCategory._id
  }

  it should "refuse to save the result to the source data set" in {
    val fixture = new Fixture

    val error = fixture.runFailure(spec(resultDataSetSpec = ResultDataSetSpec(sourceId, "Examinations")))

    error shouldBe an[AdaException]
    error.getMessage should include("must be a new one")
    fixture.saveCalls shouldBe empty
  }

  it should "refuse an invalid spec before saving anything" in {
    val fixture = new Fixture

    val error = fixture.runFailure(spec(Seq(GroupByAggregation("first_visit", GroupByFunction.Min, Seq("VISIT_DATE")))))

    error.getMessage should include("'VISIT_DATE' not found")
    fixture.saveCalls shouldBe empty
  }

  "Group by transformation JSON" should "round-trip, with the defaults of the aggregations if missing" in {
    val transformation = spec()
    val json = Json.toJson(transformation: DataSetMetaTransformation)

    json.as[DataSetMetaTransformation] shouldBe transformation

    val aggregationsJson = Json.arr(
      Json.obj("outFieldName" -> "examinations", "function" -> "Count"),
      Json.obj(
        "outFieldName" -> "xray_visits", "function" -> "CountDistinct", "inFieldNames" -> Seq("VISIT_ID"),
        "conditions" -> Json.arr(Json.obj("fieldName" -> "tests.xray", "conditionType" -> "=", "value" -> "Ordered"))
      )
    )

    (json.as[JsObject] + ("aggregations" -> aggregationsJson)).as[DataSetMetaTransformation]
      .asInstanceOf[GroupByTransformation].aggregations shouldBe Seq(
        GroupByAggregation("examinations", GroupByFunction.Count),
        GroupByAggregation(
          "xray_visits", GroupByFunction.CountDistinct, Seq("VISIT_ID"),
          conditions = Seq(FilterCondition("tests.xray", None, ConditionType.Equals, Some("Ordered"), None))
        )
      )
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

// an in-memory store of categories, giving a saved one an id
private class CategoryStore(
  categories: Seq[Category]
) extends InMemoryStore[Category, BSONObjectID](categories, _._id.get) {

  override def save(category: Category) = super.save(category.copy(_id = Some(BSONObjectID.generate())))
}

// an in-memory store of rows, sorting them (undefined values first, as Mongo), recording the sorts and projections
private class SortingJsonStore(
  initRows: Seq[JsObject]
) extends InMemoryStore[JsObject, BSONObjectID](initRows, JsObjectIdentity.of(_).get) {

  import scala.concurrent.ExecutionContext.Implicits.global
  import org.edena.store.json.BSONObjectIDFormat

  val sorts = new ConcurrentLinkedQueue[Seq[Sort]]()
  val projections = new ConcurrentLinkedQueue[Seq[String]]()

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = Future {
    sorts.add(sort)
    projections.add(projection.toSeq)

    val ordering = new Ordering[JsObject] {
      override def compare(row1: JsObject, row2: JsObject) =
        sort.iterator.map {
          case AscSort(name) => compareValues(row1, row2, name)
          case DescSort(name) => -compareValues(row1, row2, name)
        }.find(_ != 0).getOrElse(0)
    }

    items.filter(matches(criterion, _)).sorted(ordering).map(row =>
      if (projection.isEmpty) row else JsObject(row.fields.filter { case (name, _) => projection.exists(_ == name) })
    )
  }

  private def compareValues(row1: JsObject, row2: JsObject, name: String) =
    (GroupByValues.value(row1, name), GroupByValues.value(row2, name)) match {
      case (JsNull, JsNull) => 0
      case (JsNull, _) => -1
      case (_, JsNull) => 1
      case (value1, value2) =>
        if (name == JsObjectIdentity.name) value1.as[BSONObjectID].stringify.compareTo(value2.as[BSONObjectID].stringify)
        else GroupByValues.compare(value1, value2)
    }
}
