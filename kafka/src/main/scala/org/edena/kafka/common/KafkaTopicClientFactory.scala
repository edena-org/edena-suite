package org.edena.kafka.common

import org.edena.core.domain.kafka.KafkaTopicSettings
import play.api.libs.json.{Format, JsObject}

import scala.reflect.runtime.universe.TypeTag

/**
 * Factory for creating KafkaTopicClient instances that combine a producer and consumer for the
 * same topic.
 */
trait KafkaTopicClientFactory {

  /**
   * Create a new KafkaTopicClient for the given type and settings. The JSON schema is derived
   * automatically via reflection (TypeTag).
   *
   * @param settings
   *   the topic settings (shared by producer and consumer)
   * @tparam T
   *   the message type, must have implicit Format[T] and TypeTag[T]
   * @return
   *   a new KafkaTopicClient instance
   */
  def create[T: Format: TypeTag](settings: KafkaTopicSettings): KafkaTopicClient[T]

  /**
   * Create a new KafkaTopicClient for the given type and settings with an explicit JSON
   * schema. Use this when the type contains fields that cannot be handled by reflection.
   *
   * @param settings
   *   the topic settings (shared by producer and consumer)
   * @param jsonSchema
   *   the explicit JSON schema for the message type
   * @tparam T
   *   the message type, must have implicit Format[T]
   * @return
   *   a new KafkaTopicClient instance
   */
  def create[T: Format](
    settings: KafkaTopicSettings,
    jsonSchema: JsObject
  ): KafkaTopicClient[T]
}
