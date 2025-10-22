
package org.edena.kafka.examples

import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.edena.kafka.common.{ClientConfig, ProducerUtils}
import org.edena.kafka.examples.JsonFormats._
import org.edena.kafka.serializer.PlayJsonSerializer

import scala.util.Try

object ArticlePlayJsonProducer
  extends App
  with ProducerUtils
    with ClientConfig {

  private val fullConfig = ConfigFactory.load()
  private val producerConfig = getTransactionalProducerConfig(fullConfig, "kafka.producer")
  private val serde = fullConfig.getConfig("kafka.serde").asJavaMap
  private val topic = "scala-articles-avro"

  val keySerializer: StringSerializer = new StringSerializer()

  val valueSerializer: Serializer[Article] = PlayJsonSerializer[Article]
  valueSerializer.configure(serde, false)

  private val producer =
    new KafkaProducer(producerConfig, keySerializer, valueSerializer)
  private val articles = Seq.fill(10)(Generator.articles).flatten

  Try {
    producer.initTransactions()
    producer.beginTransaction()
    for (article <- articles) {
      produce(producer, topic, article.id, article)
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
