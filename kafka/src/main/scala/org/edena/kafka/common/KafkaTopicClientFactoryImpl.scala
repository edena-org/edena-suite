package org.edena.kafka.common

import com.google.inject.Inject
import org.edena.core.domain.kafka.KafkaTopicSettings
import org.edena.core.util.LoggingSupport
import play.api.libs.json.{Format, JsObject}

import scala.reflect.runtime.universe.TypeTag

class KafkaTopicClientFactoryImpl @Inject() (
  producerFactory: KafkaProducerFactory,
  consumerFactory: KafkaConsumerFactory
) extends KafkaTopicClientFactory
    with LoggingSupport {

  override def create[T: Format: TypeTag](
    settings: KafkaTopicSettings
  ): KafkaTopicClient[T] = {
    createClient(producerFactory.create[T](settings), settings)
  }

  override def create[T: Format](
    settings: KafkaTopicSettings,
    jsonSchema: JsObject
  ): KafkaTopicClient[T] = {
    createClient(producerFactory.create[T](settings, jsonSchema), settings)
  }

  private def createClient[T: Format](
    producer: KafkaProducer[T],
    settings: KafkaTopicSettings
  ): KafkaTopicClient[T] = {
    logger.info(s"Creating Kafka topic client for topic '${settings.topic}'")

    // Ensure topic exists before creating consumer
    producer.ensureTopicExists()

    KafkaTopicClient(
      producer = producer,
      consumer = consumerFactory.create[T](settings)
    )
  }
}
