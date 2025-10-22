package org.edena.kafka.serializer

import org.apache.kafka.common.serialization.Serializer
import play.api.libs.json.{Json, Writes}

import java.nio.charset.StandardCharsets

object PlayJsonSerializer {
  def apply[T](implicit writes: Writes[T]): Serializer[T] = new PlayJsonSerializer[T]

  /**
   * A generic JSON serializer that uses Play JSON's `Writes[T]`.
   *
   * @param writes An implicit Writes[T] instance to convert T to JsValue
   * @tparam T The type we want to serialize to JSON
   */
  private class PlayJsonSerializer[T](implicit writes: Writes[T]) extends Serializer[T] {

    override def configure(configs: java.util.Map[String,_], isKey: Boolean): Unit = {
      // No special configuration needed in this case
    }

    override def serialize(topic: String, data: T): Array[Byte] = Option(data)
      .map { data =>
        val jsonString = Json.toJson(data).toString()
        jsonString.getBytes(StandardCharsets.UTF_8)
      }.getOrElse(Array.emptyByteArray)

    override def close(): Unit = {
      // No resources to close
    }
  }
}

