package org.edena.kafka.serializer

import org.apache.kafka.common.serialization.Deserializer
import play.api.libs.json._

import java.nio.ByteBuffer
import scala.util.{Failure, Success, Try}

class PlayJsonSchemaDeserializer[T: Reads] extends Deserializer[T] {

  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()

  override def deserialize(topic: String, data: Array[Byte]): T =
    if (data == null) null.asInstanceOf[T]
    else {
      val buffer = ByteBuffer.wrap(data)

      // Check if message has Confluent Schema Registry wire format (magic byte 0x00)
      val jsonBytes = if (buffer.remaining() >= 5 && buffer.get(0) == 0.toByte) {
        // Skip magic byte (1 byte) + schema ID (4 bytes)
        buffer.position(5)
        val payload = new Array[Byte](buffer.remaining())
        buffer.get(payload)
        payload
      } else {
        // Plain JSON without prefix
        data
      }

      val jsonString = new String(jsonBytes, "UTF-8")

      // Deserialize using Play JSON
      Try(Json.parse(jsonString).as[T]) match {
        case Success(value) => value
        case Failure(exception) =>
          throw new RuntimeException(
            s"Failed to deserialize JSON: ${exception.getMessage}",
            exception
          )
      }
    }

  override def close(): Unit = ()
}