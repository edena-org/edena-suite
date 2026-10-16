package org.edena.ada.server.services.importers

import java.lang.reflect.{InvocationHandler, Method, Proxy}

import org.edena.ada.server.models.Field
import org.edena.core.field.FieldTypeId
import org.edena.core.store.CrudStore
import org.edena.store.elastic.ElasticCrudStoreExtra
import org.edena.store.json.StoreTypes.JsonCrudStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SynapseNewFieldMappingTest extends AnyFlatSpec with Matchers {

  private def store(
    elastic: Boolean,
    putMappings: Seq[String] => Future[Unit] = _ => Future.successful(())
  ): (JsonCrudStore, ListBuffer[Seq[String]]) = {
    val calls = ListBuffer[Seq[String]]()
    val interfaces = if (elastic) Array[Class[_]](classOf[CrudStore[_, _]], classOf[ElasticCrudStoreExtra[_, _]]) else Array[Class[_]](classOf[CrudStore[_, _]])

    val proxy = Proxy.newProxyInstance(
      getClass.getClassLoader,
      interfaces,
      new InvocationHandler {
        override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]) =
          method.getName match {
            case "putFieldMappings" =>
              val names = args(0).asInstanceOf[Traversable[String]].toList
              calls += names
              putMappings(names)
            case "toString" => "store stub"
            case name => throw new UnsupportedOperationException(name)
          }
      }
    )
    (proxy.asInstanceOf[JsonCrudStore], calls)
  }

  private def await(future: Future[Unit]) = Await.result(future, 10.seconds)

  private val existingFields = Seq(Field("ROW_ID", fieldType = FieldTypeId.Integer), Field("name"))
  private val importedFields = existingFields ++ Seq(Field("description", fieldType = FieldTypeId.Text), Field("code"))

  "Synapse re-import" should "map only the fields new to the data set (before saving)" in {
    val (elasticStore, calls) = store(elastic = true)

    await(SynapseDataSetImporter.mapNewFields(elasticStore, existingFields, importedFields))

    calls should be (Seq(Seq("description", "code")))
  }

  it should "not fail the import if the mapping is rejected" in {
    val (elasticStore, calls) = store(elastic = true, _ => Future.failed(new IllegalStateException("mapper conflict")))

    await(SynapseDataSetImporter.mapNewFields(elasticStore, existingFields, importedFields)) // no exception

    calls.size should be (1)
  }

  it should "do nothing without new fields or for non-Elasticsearch stores" in {
    val (elasticStore, elasticCalls) = store(elastic = true)
    await(SynapseDataSetImporter.mapNewFields(elasticStore, existingFields, existingFields))
    elasticCalls should be (empty)

    val (mongoStore, _) = store(elastic = false)
    await(SynapseDataSetImporter.mapNewFields(mongoStore, existingFields, importedFields)) // no ES calls possible
  }
}
