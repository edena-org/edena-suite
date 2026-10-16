package org.edena.ada.server.dataaccess.dataset

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.concurrent.atomic.AtomicInteger

import org.edena.core.store.CrudStore
import org.edena.store.json.StoreTypes.JsonCrudStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class DataSetStoreUpdateTest extends AnyFlatSpec with Matchers {

  // a CRUD store stub: `find` returns nothing, everything else is unsupported
  private def storeStub[T]: T =
    Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(classOf[CrudStore[_, _]]),
      new InvocationHandler {
        override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]) =
          method.getName match {
            // default argument values (e.g. find's `sort = Nil`) come from the interface itself
            case name if method.isDefault && name.contains("$default$") =>
              InvocationHandler.invokeDefault(proxy, method, Option(args).getOrElse(Array.empty[AnyRef]): _*)
            case "find" => Future(Nil)
            case "toString" => "store stub"
            case "hashCode" => Int.box(System.identityHashCode(proxy))
            case "equals" => Boolean.box(proxy eq args(0))
            case name => throw new UnsupportedOperationException(name)
          }
      }
    ).asInstanceOf[T]

  "Data set store update" should "not block later updates after a failed store creation" in {
    val creations = new AtomicInteger(0)
    val newStore = storeStub[JsonCrudStore]

    val dsa = new DataSetAccessorImpl(
      "test.data_set",
      storeStub, storeStub, storeStub, storeStub, storeStub, storeStub,
      dataSetStoreCreate = (_, _) =>
        if (creations.incrementAndGet() == 1)
          Future.failed(new IllegalStateException("Elasticsearch unreachable"))
        else
          Future(newStore),
      dataSetMetaInfoStoreCreate = _ => storeStub,
      initDataSetMetaInfoStore = storeStub,
      dataSetSettingStore = storeStub
    )

    Try(Await.result(dsa.updateDataSetStore, 10.seconds)).failed.get.getMessage should be ("Elasticsearch unreachable")

    // used to hang forever here: the failed update never released its lock
    Await.result(dsa.updateDataSetStore, 10.seconds)

    creations.get should be (2)
    dsa.dataSetStore should be theSameInstanceAs newStore
  }
}
