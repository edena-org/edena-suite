package org.edena.store.elastic.json.projection

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl}
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.core.store._
import org.edena.store.elastic.ElasticBaseTest
import org.edena.store.elastic.json.ElasticJsonCrudStoreImpl
import org.edena.store.json.BSONObjectIDFormat
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.Await

/**
  * A stream sorted by a field and the `_id`, over several scroll pages, with a projection of fields with literal dotted
  * names, as the group-by transformation reads its source: the rows of a group must come adjacent.
  */
class ElasticSortedStreamTest extends AsyncFlatSpec with BeforeAndAfterAll with ElasticBaseTest {
  private implicit val materializer: Materializer = instance[Materializer]
  private implicit val ec: scala.concurrent.ExecutionContext = materializer.executionContext
  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]
  private val client = instance[ElasticClient]
  private val index = "test_json_sorted_stream"
  private val fields = Seq(
    "PATIENT_ID" -> FieldTypeSpec(FieldTypeId.String),
    "visit.type" -> FieldTypeSpec(FieldTypeId.String),
    "visit.conditions" -> FieldTypeSpec(FieldTypeId.String, isArray = true),
    "AGE" -> FieldTypeSpec(FieldTypeId.Integer)
  )

  // 2500 rows of 270 patients interleaved (i % 300, not a multiple of 10), every 10th row without a patient
  private val docs = (0 until 2500).map { i =>
    Json.obj(
      "_id" -> Json.toJson(BSONObjectID.generate())(BSONObjectIDFormat),
      "visit.type" -> s"type ${i % 3}",
      "visit.conditions" -> Seq(s"condition ${i % 5}", "None"),
      "AGE" -> i
    ) ++ (if (i % 10 == 0) Json.obj() else Json.obj("PATIENT_ID" -> f"P${i % 300}%03d"))
  }

  // the store creates its index (if missing) on construction, so it's created only after the cleanup
  private lazy val store = new ElasticJsonCrudStoreImpl(index, fields, None, false, client, config)

  private def deleteIndex() = {
    import ElasticDsl._
    client.execute(ElasticDsl.deleteIndex(index))
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    Await.result(
      for {
        _ <- deleteIndex()
        _ <- store.save(docs)
        _ <- store.refresh
      } yield (),
      2.minutes
    )
  }

  override def afterAll(): Unit = {
    Await.result(deleteIndex(), 1.minute)
    super.afterAll()
  }

  "A stream sorted by a field and the id" should "return the rows of a value adjacent, projected to dotted fields" in {
    for {
      source <- store.findAsStream(
        sort = Seq(AscSort("PATIENT_ID"), AscSort("_id")),
        projection = Seq("PATIENT_ID", "visit.type", "visit.conditions"),
        batchSize = Some(200)
      )
      rows <- source.runWith(Sink.seq)
    } yield {
      rows.size shouldBe 2500

      val patientIds = rows.map(row => (row \ "PATIENT_ID").asOpt[String])
      // the defined values sorted, the undefined ones last, each value in a run
      patientIds.flatten shouldBe patientIds.flatten.sorted
      patientIds.takeRight(250).flatten shouldBe empty
      val runs = patientIds.foldLeft(List.empty[Option[String]]) {
        case (previous :: rest, patientId) if previous == patientId => previous :: rest
        case (runs, patientId) => patientId :: runs
      }
      runs.size shouldBe 271 // and the undefined value

      // the projected fields (and no other)
      rows.flatMap(_.keys).toSet shouldBe Set("PATIENT_ID", "visit.type", "visit.conditions")
      rows.map(row => (row \ "visit.conditions").as[Seq[String]].last).toSet shouldBe Set("None")
      rows.map(row => (row \ "visit.type").as[String]).toSet shouldBe Set("type 0", "type 1", "type 2")
    }
  }

  it should "come in the order of the ids within a value" in {
    for {
      source <- store.findAsStream(sort = Seq(AscSort("PATIENT_ID"), AscSort("_id")), projection = Seq("PATIENT_ID", "AGE"))
      rows <- source.runWith(Sink.seq)
    } yield {
      // the ids are generated in the order of the ages, so within a patient the ages ascend
      val agesByPatientId = rows.groupBy(row => (row \ "PATIENT_ID").asOpt[String]).view.mapValues(_.map(row => (row \ "AGE").as[Int])).toMap
      all(agesByPatientId.values.map(ages => ages == ages.sorted)) shouldBe true
      agesByPatientId.size shouldBe 271
    }
  }
}
