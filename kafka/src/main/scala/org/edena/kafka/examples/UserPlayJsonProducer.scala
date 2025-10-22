
package org.edena.kafka.examples

import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.{Serializer, UUIDSerializer}
import org.edena.kafka.common.{ClientConfig, ProducerUtils}
import org.edena.kafka.examples.JsonFormats._
import org.edena.kafka.serializer.PlayJsonSchemaSerializer

import scala.util.Try

object UserPlayJsonProducer
  extends App
    with ProducerUtils
    with ClientConfig {

  private val fullConfig = ConfigFactory.load()
  private val producerConfig = getTransactionalProducerConfig(fullConfig, "kafka.producer")

  private val serde = fullConfig.getConfig("kafka.serde").asJavaMap

  private val topic = "users-test"
  private val numPartitions = 4
  private val replicationFactor = 1.toShort

  val keySerializer: UUIDSerializer = new UUIDSerializer()

  val valueSerializer: Serializer[User] = new PlayJsonSchemaSerializer[User]
  valueSerializer.configure(serde, false)

  private val producer =
    new KafkaProducer(producerConfig, keySerializer, valueSerializer)
  private val users = Seq.fill(1000)(Generator.users).flatten

  Try {
    createTopic(
      topic,
      numPartitions,
      replicationFactor,
      producerConfig
    )

    producer.initTransactions()
    producer.beginTransaction()
    for (user <- users) {
      produce(producer, topic, user.id, user)
    }
    producer.commitTransaction()
    logger.info("Successfully completed kafka transaction.")
  }.recover { case error =>
    logger.error(
      "Something went wrong during kafka transaction processing. Aborting",
      error
    )
    producer.abortTransaction();
  }
  producer.close()
  System.exit(0)
}
