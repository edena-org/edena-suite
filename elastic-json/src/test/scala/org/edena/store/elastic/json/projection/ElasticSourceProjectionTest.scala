package org.edena.store.elastic.json.projection

import java.util.UUID

import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl}
import org.edena.core.UUIDIdentity
import org.edena.core.store.AscSort
import org.edena.store.elastic.ElasticFieldMappingExtra._
import org.edena.store.elastic.dynamic.ElasticDynamicReadonlyStoreFactory
import org.edena.store.elastic.json.format.ElasticFormatCrudStore
import org.edena.store.elastic.{ElasticBaseTest, ElasticCrudStoreExtraImpl, ElasticSetting}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{Json, OFormat}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * A projection with a nested field is read from _source, whose values have their natural shapes: plain arrays must
  * come whole (not cut to their first value as the lists of stored fields are), also from a format or dynamic store.
  */
class ElasticSourceProjectionTest extends AsyncFlatSpec with BeforeAndAfterAll with ElasticBaseTest {

  import ElasticSourceProjectionTest._

  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  private val client = instance[ElasticClient]
  private val index = "test_source_projection"

  private val docs = Seq(
    TaggedDoc(Some(UUID.randomUUID()), "a", tags = Seq("x", "y"), parts = Seq(Part("p1"), Part("p2"))),
    TaggedDoc(Some(UUID.randomUUID()), "b", tags = Seq("z"), parts = Seq(Part("p3")))
  )

  private val projection = Seq("name", "tags", "parts")

  // creates the index (if missing) on construction, so it's created only after the cleanup
  private lazy val store = new TaggedDocStore(client, index)

  override def beforeAll(): Unit = {
    super.beforeAll()
    import ElasticDsl._

    Await.result(
      for {
        _ <- client.execute(deleteIndex(index))
        _ <- store.save(docs)
        _ <- store.refresh
      } yield (),
      1.minute
    )
  }

  override def afterAll(): Unit = {
    import ElasticDsl._
    Await.result(client.execute(deleteIndex(index)), 1.minute)
    super.afterAll()
  }

  "A projected find with a nested field" should "return plain arrays whole from a format store" in {
    store.find(sort = Seq(AscSort("name")), projection = projection).map { items =>
      items.toSeq shouldBe docs.map(_.copy(id = None))
    }
  }

  it should "return plain arrays whole from a dynamic store, as its value maps from _source" in {
    for {
      dynamicStore <- new ElasticDynamicReadonlyStoreFactory(client)(index)
      items <- dynamicStore.find(sort = Seq(AscSort("name")), projection = projection)
    } yield {
      items.map(_("tags")).toSeq shouldBe Seq(Seq("x", "y"), Seq("z"))
      items.map(_("parts")).toSeq shouldBe Seq(Seq(Map("title" -> "p1"), Map("title" -> "p2")), Seq(Map("title" -> "p3")))
    }
  }
}

object ElasticSourceProjectionTest {

  case class Part(title: String)

  case class TaggedDoc(id: Option[UUID], name: String, tags: Seq[String], parts: Seq[Part])

  implicit val partFormat: OFormat[Part] = Json.format[Part]
  implicit val taggedDocFormat: OFormat[TaggedDoc] = Json.format[TaggedDoc]

  implicit object TaggedDocIdentity extends UUIDIdentity[TaggedDoc] {
    override val name = "id"
    override def of(entity: TaggedDoc) = entity.id
    override protected def set(entity: TaggedDoc, id: Option[UUID]) = entity.copy(id = id)
  }

  final class TaggedDocStore(val client: ElasticClient, indexName: String) extends ElasticFormatCrudStore[TaggedDoc, UUID](
    indexName, ElasticSetting(shards = 1, replicas = 0)
  ) with ElasticCrudStoreExtraImpl[TaggedDoc, UUID] {

    createIndexIfNeeded()

    override protected def fieldDefs = Seq(
      keywordField("id") store true,
      keywordField("name") store true,
      keywordField("tags") store true,
      nestedField("parts") fields Seq(keywordField("title"))
    )
  }
}
