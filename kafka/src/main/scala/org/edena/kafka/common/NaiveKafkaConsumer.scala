package org.edena.kafka.common

import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.serialization.Deserializer

import java.time.Duration
import scala.collection.JavaConverters.{
  iterableAsScalaIterableConverter,
  mapAsJavaMapConverter,
  seqAsJavaListConverter
}

trait NaiveKafkaConsumer[K, V] {
  def run(
    topics: Seq[String]
  )(
    handleMessage: ConsumerRecord[K, V] => Unit
  ): Unit

  def shutdown(): Unit
}

class DefaultNaiveKafkaConsumer[K, V](
  consumerProps: Map[String, AnyRef],
  keyDeserializer: Deserializer[K],
  valueDeserializer: Deserializer[V],
  pollTimeout: Duration = Duration.ofMillis(100)
) extends NaiveKafkaConsumer[K, V] {

  @volatile private var keepRunning = true

  private val consumer = new KafkaConsumer[K, V](consumerProps.asJava, keyDeserializer, valueDeserializer)

  /**
   * Subscribes to given topics and starts polling in an infinite loop, invoking `handleMessage` for each record.
   */
  override def run(topics: Seq[String])(handleMessage: ConsumerRecord[K, V] => Unit): Unit = {
    // Subscribe to topics
    consumer.subscribe(topics.asJava)

    // If you really want to read from the beginning each time, do so here
    // consumer.poll(Duration.ofMillis(0)) // Force initial partition assignment
    // consumer.seekToBeginning(consumer.assignment()) // or do it after assignment

    try {
      while (keepRunning) {
        val records = consumer.poll(pollTimeout)
        for (record <- records.asScala) {
          try {
            handleMessage(record)
          } catch {
            case e: Throwable =>
              // Log and decide whether to keep processing or not
              println(s"Error processing record: ${e.getMessage}")
            // Possibly break or continue depending on your use-case
          }
        }
        // If using manual offset commit, do it here
        // consumer.commitSync()
      }
    } catch {
      case e: Throwable =>
        println(s"Unexpected error in consumer loop: ${e.getMessage}")
    } finally {
      // Ensure consumer is closed at the end
      consumer.close()
    }
  }

  override def shutdown(): Unit = {
    keepRunning = false
  }
}
