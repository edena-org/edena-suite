package org.edena.kafka.common

/**
 * Combines a KafkaProducer and KafkaConsumer for the same topic.
 * Since producers and consumers for a topic always come together,
 * this simplifies dependency injection and provides a cleaner API.
 *
 * @param producer the Kafka producer for this topic
 * @param consumer the Kafka consumer for this topic
 * @tparam T the type of messages handled by this client
 */
case class KafkaTopicClient[T](
  producer: KafkaProducer[T],
  consumer: KafkaConsumer[T]
)
