package org.edena.kafka.common

import com.google.inject.Inject
import com.typesafe.config.Config
import org.apache.kafka.common.serialization.Serializer
import org.edena.core.domain.kafka.KafkaTopicSettings
import org.edena.core.util.LoggingSupport
import org.edena.kafka.serializer.{PlayJsonExplSchemaSerializer, PlayJsonSchemaSerializer}
import play.api.libs.json.{Format, JsObject}

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe.TypeTag

/**
 * Factory for creating typed Kafka producers.
 */
trait KafkaProducerFactory {

  /**
   * Create a new Kafka producer for the given type and settings.
   * The JSON schema is derived automatically via reflection (TypeTag).
   *
   * @param settings
   *   the producer settings (topic, partitions, replication, transactional)
   * @tparam T
   *   the message type, must have implicit Format[T] and TypeTag[T]
   * @return
   *   a new KafkaProducer instance
   */
  def create[T: Format: TypeTag](settings: KafkaTopicSettings): KafkaProducer[T]

  /**
   * Create a new Kafka producer for the given type and settings with an explicit JSON schema.
   * Use this when the type contains fields that cannot be handled by reflection
   * (e.g., ModelParameters).
   *
   * @param settings
   *   the producer settings (topic, partitions, replication, transactional)
   * @param jsonSchema
   *   the explicit JSON schema for the message type
   * @tparam T
   *   the message type, must have implicit Format[T]
   * @return
   *   a new KafkaProducer instance
   */
  def create[T: Format](settings: KafkaTopicSettings, jsonSchema: JsObject): KafkaProducer[T]
}

class KafkaProducerFactoryImpl @Inject() (
  config: Config
)(
  implicit ec: ExecutionContext
) extends KafkaProducerFactory with LoggingSupport with ClientConfig {

  override def create[T: Format: TypeTag](
    settings: KafkaTopicSettings
  ): KafkaProducer[T] = {
    val valueSerializer: Serializer[T] = {
      val serdeConfig = config.getConfig("kafka.serde").asJavaMap
      val serializer = new PlayJsonSchemaSerializer[T]
      serializer.configure(serdeConfig, isKey = false)
      serializer
    }

    createProducer(settings, valueSerializer)
  }

  override def create[T: Format](
    settings: KafkaTopicSettings,
    jsonSchema: JsObject
  ): KafkaProducer[T] = {
    val valueSerializer: Serializer[T] = {
      val serdeConfig = config.getConfig("kafka.serde").asJavaMap
      val serializer = new PlayJsonExplSchemaSerializer[T](jsonSchema)
      serializer.configure(serdeConfig, isKey = false)
      serializer
    }

    createProducer(settings, valueSerializer)
  }

  private def createProducer[T: Format](
    settings: KafkaTopicSettings,
    valueSerializer: Serializer[T]
  ): KafkaProducer[T] = {
    logger.info(
      s"Creating Kafka producer for topic '${settings.topic}' " +
        s"(partitions=${settings.numPartitions}, replication=${settings.replicationFactor}, " +
        s"transactional=${settings.transactionalEnabled})"
    )

    new KafkaProducerImpl[T](settings, config, valueSerializer)
  }
}
