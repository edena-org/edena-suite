package scala.org.edena.ada.server.dataaccess.mongo

import org.edena.core.Identity
import org.edena.core.store.Criterion._
import org.edena.store.json.BSONObjectIdentity
import org.scalatest.{Filter => _, _}
import play.api.libs.json.{Format, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{MongoCrudStore, ReactiveMongoApi}

import scala.concurrent.Future
import scala.org.edena.ada.server.services.InjectorWrapper

class MongoCrudStoreSpec extends AsyncFlatSpec {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  case class Entity(
    _id: Option[BSONObjectID] = Some(BSONObjectID.generate()),
    str: String = "ABC",
    int: Int = 123,
    strSeq: Seq[String] = List("A", "B", "C"),
    intSeq: Seq[Int] = List(1, 2, 3),
    bool: Boolean = true
  )

  implicit object EntityIdentity extends BSONObjectIdentity[Entity] {
    override def of(entity: Entity): Option[BSONObjectID] = entity._id
    override protected def set(entity: Entity, id: Option[BSONObjectID]): Entity = entity.copy(_id = id)
  }

  implicit val entityFormat = Json.format[Entity]

  private def withMongoCrudStore[E: Format, ID: Format]
  (testCode: MongoCrudStore[E, ID] => Future[Assertion])
  (implicit identity: Identity[E, ID]) = {
    val repo = new MongoCrudStore[E, ID]("testCollection")
    repo.reactiveMongoApi = InjectorWrapper.instanceOf[ReactiveMongoApi]
    for {
      _ <- testCode(repo)
      _ <- repo.deleteAll
    } yield succeed
  }

  behavior of "MongoCrudStore"

  it should "save Entity and check if it exists" in withMongoCrudStore[Entity, BSONObjectID] { repo =>
    val entity = Entity()
    val id = EntityIdentity of entity getOrElse fail
    for {
      _ <- repo.save(entity)
      _ <- repo.flushOps
      exists <- repo.exists(id)
    } yield assert(exists)
  }

  it should "save and get Entity" in withMongoCrudStore[Entity, BSONObjectID] { repo =>
    val entity = Entity()
    val id = EntityIdentity of entity getOrElse fail
    for {
      _ <- repo.save(entity)
      _ <- repo.flushOps
      entry <- repo.get(id)
      retrievedEntity = entry getOrElse fail
    } yield {
      assert(entity == retrievedEntity)
      assert(retrievedEntity._id.getOrElse(fail).stringify == id.stringify)
      assert(retrievedEntity.bool)
      assert(retrievedEntity.int == 123)
      assert(retrievedEntity.str == "ABC")
      assert(retrievedEntity.intSeq == List(1, 2, 3))
      assert(retrievedEntity.strSeq == List("A", "B", "C"))
    }
  }

  it should "delete a created Entity" in withMongoCrudStore[Entity, BSONObjectID] { repo =>
    val entity = Entity()
    val id = EntityIdentity of entity getOrElse fail
    for {
      _ <- repo.save(entity)
      _ <- repo.flushOps
      _ <- repo.delete(id)
      _ <- repo.flushOps
      entry <- repo.get(id)
    } yield assert(entry.isEmpty)
  }

  it should "find a created Entity by criteria" in withMongoCrudStore[Entity, BSONObjectID] { repo =>
    val entity1 = Entity(int = 1, str = "A")
    val entity2 = Entity(int = 2, str = "B")
    for {
      _ <- repo.save(entity1)
      _ <- repo.save(entity2)
      _ <- repo.flushOps
      entry1 <- repo.find("int" #== 1)
      entry2 <- repo.find("int" #== 2)
      entry3 <- repo.find("int" #== 3)
    } yield {
      assert(entry1.size == 1)
      assert(entry2.size == 1)
      assert(entry3.isEmpty)
      assert(entry1.head.str == "A")
      assert(entry2.head.str == "B")
    }
  }

  it should "update a created Entity" in withMongoCrudStore[Entity, BSONObjectID] { repo =>
    val oldEntity = Entity()
    val newEntity = oldEntity.copy(strSeq = List("D", "E", "F"))
    val id = EntityIdentity of oldEntity getOrElse fail
    for {
      _ <- repo.save(oldEntity)
      _ <- repo.flushOps
      _ <- repo.update(newEntity)
      _ <- repo.flushOps
      entry <- repo.get(id)
      retrievedEntity = entry getOrElse fail
    } yield {
      assert(oldEntity != retrievedEntity)
      assert(newEntity == retrievedEntity)
      assert(retrievedEntity.strSeq == newEntity.strSeq)
      assert(retrievedEntity.strSeq != oldEntity.strSeq)
    }
  }

  it should "can count created Entities" in withMongoCrudStore[Entity, BSONObjectID] { repo =>
    val entities = List(Entity(int = 1), Entity(int = 2), Entity(int = 3), Entity(int = 4), Entity(int = 5))
    for {
      _ <- Future.sequence(entities map repo.save)
      count <- repo.count("int" #> 2)
    } yield assert(count == 3)
  }
}