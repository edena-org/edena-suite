package org.edena.kafka.serializer

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.kafka.common.serialization.Serializer
import org.edena.json.util.JsonSchemaReflectionHelper
import play.api.libs.json._

import java.nio.ByteBuffer
import scala.reflect.runtime.universe._

class PlayJsonSchemaSerializer[T: Writes: TypeTag]
  extends Serializer[T]
  with JsonSchemaReflectionHelper {

  private val jsonSchema: JsonSchema = {
    val schemaJson = jsonSchemaFor[T]()
    val schemaString = Json.stringify(schemaJson)
    new JsonSchema(schemaString)
  }

  private var schemaRegistryClient: CachedSchemaRegistryClient = _
  private var isKey: Boolean = false

  override def configure(c: java.util.Map[String, _], isKey: Boolean): Unit = {
    this.isKey = isKey
    val schemaRegistryUrl = c.get("schema.registry.url").asInstanceOf[String]
    schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 100)
  }

  override def serialize(topic: String, data: T): Array[Byte] =
    if (data == null) null else {
      // Register schema and get schema ID
      val subject = topic + (if (isKey) "-key" else "-value")
      val schemaId = schemaRegistryClient.register(subject, jsonSchema)

      // Serialize JSON data using Play JSON
      val jsonBytes = Json.toJson(data).toString.getBytes("UTF-8")

      // Serialize with schema registry wire format: [magic_byte][schema_id][json_data]
      val buffer = ByteBuffer.allocate(5 + jsonBytes.length)
      buffer.put(0.toByte) // magic byte
      buffer.putInt(schemaId) // schema ID from registry
      buffer.put(jsonBytes) // actual JSON payload
      buffer.array()
    }

  override def close(): Unit = ()
}
