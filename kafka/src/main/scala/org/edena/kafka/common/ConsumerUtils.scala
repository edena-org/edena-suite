package org.edena.kafka.common

import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.consumer.{ConsumerRecords, KafkaConsumer}
import org.slf4j.LoggerFactory

import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.IterableHasAsScala

trait ConsumerUtils[T] {

  protected val logger = Logger(LoggerFactory.getLogger(this.getClass))

  def pool[K, V](
    consumer: KafkaConsumer[K, V],
    timeout: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
  ): Iterable[(K, V)] = {

    val records: ConsumerRecords[K, V] =
      consumer.poll(
        // scala to java duration
        JDuration.ofNanos(timeout.toNanos)
        // new ScalaDurationOps(timeout).toJava
      )

    val messages = records.asScala.map(record => {
      logger.debug(
        s"received record from topic ${record.topic}. Key:  ${record.key} value: ${record.value.toString}"
      )
      (record.key, record.value)
    })
    messages
  }
}