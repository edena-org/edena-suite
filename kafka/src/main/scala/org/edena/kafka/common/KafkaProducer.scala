package org.edena.kafka.common

import org.apache.kafka.clients.producer.RecordMetadata

import java.util.UUID
import scala.concurrent.Future

/**
 * Generic Kafka producer trait for any type T with a Play JSON Format.
 *
 * @tparam T
 *   the type of messages to produce
 */
trait KafkaProducer[T] {

  /** The Kafka topic this producer writes to */
  def topic: String

  /** Send a single message with the given key */
  def send(key: UUID, value: T): Future[RecordMetadata]

  /** Send multiple messages in parallel (non-transactional) */
  def sendBatch(items: Seq[(UUID, T)]): Future[Seq[RecordMetadata]]

  /** Send multiple messages in a single transaction */
  def sendTransactional(items: Seq[(UUID, T)]): Future[Unit]

  /** Close the producer and release resources */
  def close(): Unit

  /** Ensure the topic exists with correct settings (called during initialization) */
  def ensureTopicExists(): Unit
}
