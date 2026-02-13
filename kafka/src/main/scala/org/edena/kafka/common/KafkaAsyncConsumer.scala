package org.edena.kafka.common

import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.Future

trait KafkaAsyncConsumer[K, V] {
  def run(
    processRecord: ConsumerRecord[K, V] => Future[Unit]
  ): Unit
}
