package org.edena.kafka.serializer

import org.apache.kafka.common.serialization.Deserializer
import play.api.libs.json.{Json, Reads}

import java.nio.charset.StandardCharsets

/**
 * A generic JSON deserializer that uses Play JSON’s `Reads[T]`.
 *
 * @param reads An implicit Reads[T] instance to convert JSON to T
 * @tparam T The type we want to deserialize from JSON
 */
object PlayJsonDeserializer {
  def apply[T](implicit reads: Reads[T]): Deserializer[T] = new PlayJsonDeserializer[T]

  private class PlayJsonDeserializer[T](implicit reads: Reads[T]) extends Deserializer[T] {

    override def configure(configs: java.util.Map[String,_], isKey: Boolean): Unit = {
      // No special configuration needed in this case
    }

    override def deserialize(topic: String, data: Array[Byte]): T =
      Option(data).map { data =>
        val json = Json.parse(new String(data, StandardCharsets.UTF_8))
        json.as[T]
      }.getOrElse(null.asInstanceOf[T])

    override def close(): Unit = {
      // No resources to close
    }
  }
}