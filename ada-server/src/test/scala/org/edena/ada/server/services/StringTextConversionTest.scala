package org.edena.ada.server.services

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.StoreTypes.{FieldStore, MessageStore, TranslationStore}
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.edena.ada.server.models.Field
import org.edena.core.field.FieldTypeId
import org.edena.core.store.{CrudStore, InCriterion, StreamStore}
import org.edena.store.elastic.ElasticCrudStoreExtra
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reactivemongo.api.bson.BSONObjectID

import scala.collection.mutable.{ListBuffer, Map => MMap}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Try

// String <-> Text conversion of DataSetService with in-memory stubs (dynamic proxies) instead of Mongo/Elasticsearch
class StringTextConversionTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("StringTextConversionTest")
  private implicit val materializer: Materializer = Materializer(system)
  import system.dispatcher

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 30.seconds)
    super.afterAll()
  }

  private val dataSetId = "test.data_set"

  // a stub implementing the given interfaces, delegating to `handle` (by method name and args)
  private def stub[T](interfaces: Class[_]*)(handle: PartialFunction[(String, Seq[AnyRef]), AnyRef]): T =
    Proxy.newProxyInstance(
      getClass.getClassLoader,
      interfaces.toArray,
      new InvocationHandler {
        override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]) = {
          val nameAndArgs = (method.getName, Option(args).map(_.toSeq).getOrElse(Nil))
          nameAndArgs match {
            // default argument values (e.g. find's `sort = Nil`) come from the interface itself
            case (name, _) if method.isDefault && name.contains("$default$") =>
              InvocationHandler.invokeDefault(proxy, method, Option(args).getOrElse(Array.empty[AnyRef]): _*)
            case ("toString", Nil) => "stub"
            case ("hashCode", Nil) => Int.box(System.identityHashCode(proxy))
            case ("equals", Seq(other)) => Boolean.box(proxy eq other)
            case _ if handle.isDefinedAt(nameAndArgs) => handle(nameAndArgs)
            case _ => throw new UnsupportedOperationException(s"Stub method '${method.getName}' not supported.")
          }
        }
      }
    ).asInstanceOf[T]

  private class Fixture(
    initFields: Seq[Field],
    elastic: Boolean = true,
    reindex: Seq[String] => Future[Unit] = _ => Future(()),
    failStoreUpdateAttempts: Set[Int] = Set()
  ) {
    val fields = MMap[String, Field](initFields.map(field => (field.name, field)): _*)
    val reindexCalls = ListBuffer[Seq[String]]()
    val storeUpdates = new AtomicInteger(0)

    private val fieldStore: FieldStore = stub[FieldStore](classOf[CrudStore[_, _]]) {
      case ("find", Seq(criterion, _*)) =>
        val names = criterion match {
          case InCriterion(_, values) => values.map(_.toString).toSet
          case _ => fields.keySet.toSet
        }
        Future(fields.values.filter(field => names.contains(field.name)).toList)

      case ("update", Seq(field: Field)) =>
        Future { fields.update(field.name, field); field.name }

      case ("update", Seq(entities: Traversable[_])) =>
        Future {
          entities.map { case field: Field => fields.update(field.name, field); field.name }
        }
    }

    private val dataSetStore =
      if (elastic)
        stub[AnyRef](classOf[CrudStore[_, _]], classOf[ElasticCrudStoreExtra[_, _]]) {
          case ("updateFieldMappingsInPlace", Seq(fieldNames: Traversable[_])) =>
            val names = fieldNames.map(_.toString).toList
            reindexCalls += names
            reindex(names)
        }
      else
        stub[AnyRef](classOf[CrudStore[_, _]])(PartialFunction.empty)

    private val dsa: DataSetAccessor = stub[DataSetAccessor](classOf[DataSetAccessor]) {
      case ("dataSetId", Nil) => dataSetId
      case ("fieldStore", Nil) => fieldStore
      case ("dataSetStore", Nil) => dataSetStore
      case ("updateDataSetStore", Nil) =>
        val attempt = storeUpdates.incrementAndGet()
        if (failStoreUpdateAttempts.contains(attempt))
          Future.failed(new IllegalStateException(s"Store update #$attempt failed"))
        else
          Future(())
    }

    private val dsaf = stub[DataSetAccessorFactory](classOf[DataSetAccessorFactory]) {
      case ("apply", Seq(`dataSetId`)) => Future(Some(dsa))
      case ("getOrError", Seq(`dataSetId`)) => Future(dsa)
    }

    private val messageStore = stub[MessageStore](classOf[StreamStore[_, _]]) {
      case ("save", Seq(_)) => Future(BSONObjectID.generate())
    }

    private val translationStore = stub[TranslationStore](classOf[CrudStore[_, _]])(PartialFunction.empty)

    val service = new DataSetServiceImpl(dsaf, translationStore, messageStore)

    def convert(fieldNames: Seq[String], toText: Boolean) =
      service.convertStringAndTextFields(dataSetId, fieldNames, toText)

    def typeOf(name: String) = fields(name).fieldType
  }

  private def result[T](future: Future[T]) = Await.result(future, 30.seconds)

  private def failure(future: Future[_]) = {
    val error = Try(result(future)).failed.get
    error shouldBe an[AdaException]
    error.getMessage
  }

  private val stringField = Field("notes", fieldType = FieldTypeId.String)
  private val textField = Field("summary", fieldType = FieldTypeId.Text)
  private val intField = Field("count", fieldType = FieldTypeId.Integer)

  "String to Text conversion" should "change the types and re-index all the requested String/Text fields" in {
    val fixture = new Fixture(Seq(stringField, textField, intField))

    result(fixture.convert(Seq("notes", "summary", "count", "unknown"), toText = true)) should be (Seq("notes"))

    fixture.typeOf("notes") should be (FieldTypeId.Text)
    fixture.typeOf("summary") should be (FieldTypeId.Text)
    fixture.typeOf("count") should be (FieldTypeId.Integer)
    fixture.reindexCalls.map(_.toSet) should be (Seq(Set("notes", "summary")))
    fixture.storeUpdates.get should be (1)
  }

  it should "roll back the field types if the re-indexing fails" in {
    val fixture = new Fixture(Seq(stringField), reindex = _ => Future.failed(new IllegalStateException("ES is down")))

    val message = failure(fixture.convert(Seq("notes"), toText = true))

    message should include ("ES is down")
    message should include ("left unchanged")
    fixture.typeOf("notes") should be (FieldTypeId.String)
    fixture.storeUpdates.get should be (2) // conversion + rollback
  }

  it should "roll back the field types if the store re-creation fails" in {
    val fixture = new Fixture(Seq(stringField), failStoreUpdateAttempts = Set(1))

    failure(fixture.convert(Seq("notes"), toText = true)) should include ("Store update #1 failed")
    fixture.typeOf("notes") should be (FieldTypeId.String)
    fixture.reindexCalls should be (empty)
  }

  it should "keep fields that already were Text if their re-indexing fails" in {
    val fixture = new Fixture(Seq(textField), reindex = _ => Future.failed(new IllegalStateException("ES is down")))

    val message = failure(fixture.convert(Seq("summary"), toText = true))

    message should include ("ES is down")
    message should not include ("left unchanged")
    fixture.typeOf("summary") should be (FieldTypeId.Text)
  }

  it should "not run concurrently for the same data set" in {
    val reindexPromise = Promise[Unit]()
    val fixture = new Fixture(Seq(stringField), reindex = _ => reindexPromise.future)

    val first = fixture.convert(Seq("notes"), toText = true)
    failure(fixture.convert(Seq("notes"), toText = true)) should include ("already in progress")

    reindexPromise.success(())
    result(first) should be (Seq("notes"))

    // released again
    result(fixture.convert(Seq("notes"), toText = true)) should be (Nil)
    fixture.reindexCalls.size should be (2)
  }

  "Text to String conversion" should "change the types without re-indexing" in {
    val fixture = new Fixture(Seq(stringField, textField))

    result(fixture.convert(Seq("notes", "summary"), toText = false)) should be (Seq("summary"))

    fixture.typeOf("summary") should be (FieldTypeId.String)
    fixture.typeOf("notes") should be (FieldTypeId.String)
    fixture.reindexCalls should be (empty)
  }

  "Conversion on a non-Elasticsearch store" should "only change the types" in {
    val fixture = new Fixture(Seq(stringField), elastic = false)

    result(fixture.convert(Seq("notes"), toText = true)) should be (Seq("notes"))
    fixture.typeOf("notes") should be (FieldTypeId.Text)
  }
}
