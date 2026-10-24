package org.edena.ada.web.services

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.edena.ada.server.models.{BoxWidgetSpec, CumulativeCountWidgetSpec, DistributionWidgetSpec, Field, WidgetGenerationMethod, WidgetSpec}
import org.edena.ada.server.services.StatsService
import org.edena.ada.web.models.{BoxWidget, CategoricalCountWidget, NumericalCountWidget, Widget}
import org.edena.core.field.FieldTypeId
import org.edena.core.store._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.libs.json.{JsNull, JsObject, Json}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._

// The widgets of Text fields are calculated from all the values (full data or streamed), never by the repo, which can't
// compare Text values (Elasticsearch analyzes them)
class TextWidgetGenerationTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("TextWidgetGenerationTest")
  private implicit val materializer: Materializer = Materializer(system)
  import system.dispatcher

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 30.seconds)
    super.afterAll()
  }

  private val fields = Seq(
    Field("note", fieldType = FieldTypeId.Text),
    Field("code", fieldType = FieldTypeId.String),
    Field("num", fieldType = FieldTypeId.Integer)
  )

  private val rows = Seq(Some("b"), Some("a"), Some("b"), Some("c"), None).zipWithIndex.map { case (note, i) =>
    Json.obj("code" -> "x", "note" -> note, "num" -> i)
  }

  private val repoBasedMethods = Seq(
    WidgetGenerationMethod.Auto,
    WidgetGenerationMethod.RepoAndFullData,
    WidgetGenerationMethod.RepoAndStreamedAll,
    WidgetGenerationMethod.RepoAndStreamedIndividually
  )

  "A distribution widget of a Text field" should "be calculated from all the values, not by the repo" in {
    val fixture = new Fixture

    repoBasedMethods.foreach { genMethod =>
      fixture.categoricalCounts(DistributionWidgetSpec("note", None), genMethod) shouldBe
        Map("All" -> Set(("b", 2), ("a", 1), ("c", 1), ("Undefined", 1)))
    }

    fixture.repoCalcs shouldBe empty
  }

  it should "be calculated from all the values if grouped by a Text field" in {
    val fixture = new Fixture

    repoBasedMethods.foreach { genMethod =>
      fixture.categoricalCounts(DistributionWidgetSpec("code", Some("note")), genMethod).keySet should contain allOf ("a", "b", "c")
    }

    fixture.repoCalcs shouldBe empty
  }

  "Box and cumulative widgets grouped by a Text field" should "be calculated from all the values, not by the repo" in {
    val fixture = new Fixture
    val specs = Seq(BoxWidgetSpec("num", Some("note")), CumulativeCountWidgetSpec("num", Some("note"), numericBinCount = Some(3)))

    repoBasedMethods.foreach { genMethod =>
      val widgets = fixture.widgets(specs, genMethod).flatten
      withClue(s"$genMethod: ") {
        widgets.size shouldBe 2
        widgets.head shouldBe a[BoxWidget[_]]
        widgets(1) shouldBe a[NumericalCountWidget[_]]
      }
    }

    // the streamed bins need the min and max of the numeric field, which the repo gives
    fixture.repoCalcs.distinct should (be(empty) or be(Seq("getNumericMinMax")))
  }

  "A distribution widget of a String field" should "still be calculated by the repo" in {
    val fixture = new Fixture

    fixture.categoricalCounts(DistributionWidgetSpec("code", None), WidgetGenerationMethod.Auto) shouldBe
      Map("All" -> Set(("x", rows.size)))

    fixture.repoCalcs shouldBe Seq("calcUniqueDistributionCountsFromRepo")
  }

  // a widget service whose stats service only records the repo calcs (answering only a unique distribution count and a
  // min/max)
  private class Fixture {
    private val repoCalcQueue = new ConcurrentLinkedQueue[String]()

    def repoCalcs: Seq[String] = repoCalcQueue.asScala.toList

    private val statsService = Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(classOf[StatsService]),
      new InvocationHandler {
        override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]) =
          method.getName match {
            case "toString" => "statsService"
            case "hashCode" => Int.box(System.identityHashCode(proxy))
            case "equals" => Boolean.box(proxy eq args(0))
            case name =>
              repoCalcQueue.add(name)
              name match {
                case "calcUniqueDistributionCountsFromRepo" => Future(Seq((Some("x"), rows.size)))
                case "getNumericMinMax" => Future((Some(0d), Some(rows.size - 1d)))
                case _ => throw new UnsupportedOperationException(s"Stats service method '$name' not supported.")
              }
          }
      }
    ).asInstanceOf[StatsService]

    private val widgetService = new WidgetGenerationServiceImpl(statsService, Configuration.empty)

    def widgets(
      specs: Seq[WidgetSpec],
      genMethod: WidgetGenerationMethod.Value
    ): Seq[Option[Widget]] =
      Await.result(
        widgetService(specs, new InMemoryJsonStore(rows), NoCriterion, Map(), fields, genMethod),
        30.seconds
      ).toSeq

    // the (value label, count) pairs of a categorical widget by series (group)
    def categoricalCounts(
      spec: DistributionWidgetSpec,
      genMethod: WidgetGenerationMethod.Value
    ): Map[String, Set[(String, Int)]] =
      widgets(Seq(spec), genMethod) match {
        case Seq(Some(widget: CategoricalCountWidget)) =>
          widget.data.map { case (series, counts) => (series, counts.map(count => (count.value, count.count)).toSet) }.toMap

        case other =>
          fail(s"A categorical count widget expected but got $other (with $genMethod).")
      }
  }
}

// an in-memory JSON store supporting only presence checks (no comparisons), and a sort by a number
private class InMemoryJsonStore(
  rows: Seq[JsObject])(
  implicit ec: ExecutionContext
) extends ReadonlyStore[JsObject, BSONObjectID] {

  override def get(id: BSONObjectID) = throw new UnsupportedOperationException

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = Future {
    val matchingRows = rows.filter(matches(criterion, _))

    val sortedRows = sort match {
      case Nil => matchingRows
      case Seq(AscSort(fieldName)) => matchingRows.sortBy(number(fieldName))
      case Seq(DescSort(fieldName)) => matchingRows.sortBy(number(fieldName)).reverse
      case _ => throw new UnsupportedOperationException(s"Sort $sort not supported.")
    }

    sortedRows.take(limit.getOrElse(Int.MaxValue))
  }

  private def number(fieldName: String)(row: JsObject) = (row \ fieldName).asOpt[BigDecimal]

  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = throw new UnsupportedOperationException

  override def count(criterion: Criterion) = Future(rows.count(matches(criterion, _)))

  private def matches(criterion: Criterion, row: JsObject): Boolean = {
    def isDefined(fieldName: String) = (row \ fieldName).toOption.exists(_ != JsNull)

    criterion match {
      case NoCriterion => true
      case And(criteria) => criteria.forall(matches(_, row))
      case EqualsNullCriterion(fieldName) => !isDefined(fieldName)
      case NotEqualsNullCriterion(fieldName) => isDefined(fieldName)
      case _ => throw new UnsupportedOperationException(s"Criterion $criterion not supported.")
    }
  }
}
