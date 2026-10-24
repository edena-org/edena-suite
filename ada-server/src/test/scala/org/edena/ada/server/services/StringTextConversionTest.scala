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

import scala.collection.mutable.{Map => MMap}
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
    elastic: Boolean = false,
    storeUpdate: () => Future[Unit] = () => Future(()),
    failStoreUpdateAttempts: Set[Int] = Set()
  ) {
    val fields = MMap[String, Field](initFields.map(field => (field.name, field)): _*)
    val fieldUpdates = new AtomicInteger(0)
    val storeUpdates = new AtomicInteger(0)

    private val fieldStore: FieldStore = stub[FieldStore](classOf[CrudStore[_, _]]) {
      case ("find", Seq(criterion, _*)) =>
        val names = criterion match {
          case InCriterion(_, values) => values.map(_.toString).toSet
          case _ => fields.keySet.toSet
        }
        Future(fields.values.filter(field => names.contains(field.name)).toList)

      case ("update", Seq(field: Field)) =>
        Future { fieldUpdates.incrementAndGet(); fields.update(field.name, field); field.name }

      case ("update", Seq(entities: Traversable[_])) =>
        Future {
          fieldUpdates.incrementAndGet()
          entities.map { case field: Field => fields.update(field.name, field); field.name }
        }
    }

    private val dataSetStore =
      if (elastic)
        stub[AnyRef](classOf[CrudStore[_, _]], classOf[ElasticCrudStoreExtra[_, _]])(PartialFunction.empty)
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
          storeUpdate()
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

  "Conversion on Elasticsearch" should "reject both directions before any dictionary or store change" in {
    Seq(true, false).foreach { toText =>
      val fixture = new Fixture(Seq(stringField, textField, intField), elastic = true)
      failure(fixture.convert(Seq("notes", "summary"), toText)) should include ("Change field types transformation")
      fixture.typeOf("notes") shouldBe FieldTypeId.String
      fixture.typeOf("summary") shouldBe FieldTypeId.Text
      fixture.fieldUpdates.get shouldBe 0
      fixture.storeUpdates.get shouldBe 0
    }
  }

  it should "leave already converted fields alone" in {
    val fixture = new Fixture(Seq(textField), elastic = true)
    result(fixture.convert(Seq("summary"), toText = true)) shouldBe Nil
    fixture.fieldUpdates.get shouldBe 0
    fixture.storeUpdates.get shouldBe 0
  }

  "Conversion on Mongo/cache" should "change string types while preserving other fields" in {
    val fixture = new Fixture(Seq(stringField, textField, intField))
    result(fixture.convert(Seq("notes", "summary", "count", "unknown"), toText = true)) shouldBe Seq("notes")
    fixture.typeOf("notes") shouldBe FieldTypeId.Text
    fixture.typeOf("summary") shouldBe FieldTypeId.Text
    fixture.typeOf("count") shouldBe FieldTypeId.Integer
    fixture.storeUpdates.get shouldBe 1
    result(fixture.convert(Seq("notes", "summary"), toText = false)).toSet shouldBe Set("notes", "summary")
    fixture.typeOf("notes") shouldBe FieldTypeId.String
    fixture.typeOf("summary") shouldBe FieldTypeId.String
  }

  it should "roll back dictionary changes if refreshing the store fails" in {
    val fixture = new Fixture(Seq(stringField), failStoreUpdateAttempts = Set(1))
    val message = failure(fixture.convert(Seq("notes"), toText = true))
    message should include ("Store update #1 failed")
    message should include ("left unchanged")
    fixture.typeOf("notes") shouldBe FieldTypeId.String
    fixture.storeUpdates.get shouldBe 2
  }

  it should "report a failed rollback" in {
    val fixture = new Fixture(Seq(stringField), failStoreUpdateAttempts = Set(1, 2))
    failure(fixture.convert(Seq("notes"), toText = true)) should include ("Restoring the original field types failed")
  }

  it should "serialize conversions and release the guard after completion" in {
    val refresh = Promise[Unit]()
    val fixture = new Fixture(Seq(stringField), storeUpdate = () => refresh.future)
    val first = fixture.convert(Seq("notes"), toText = true)
    failure(fixture.convert(Seq("notes"), toText = true)) should include ("already in progress")
    refresh.success(())
    result(first) shouldBe Seq("notes")
    result(fixture.convert(Seq("notes"), toText = true)) shouldBe Nil
  }
}
