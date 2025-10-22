package org.edena.kafka.examples

import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import org.edena.kafka.common.{ClientConfig, ConsumerUtils}
import org.edena.kafka.examples.JsonFormats._
import org.edena.kafka.serializer.PlayJsonDeserializer

import scala.collection.JavaConverters.{asJavaCollection, seqAsJavaListConverter}
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.util.Try

object ArticlePlayJsonConsumer
  extends App
  with ConsumerUtils[Article]
    with ClientConfig {

  private val fullConfig = ConfigFactory.load()
  private val consumerConfig = fullConfig.getConfig("kafka.consumer").asJavaMap
  private val serde = fullConfig.getConfig("kafka.serde").asJavaMap
  private val topic = "scala-articles-avro"

  val keyDeSerializer: StringDeserializer = new StringDeserializer()

  val valueDeSerializer: Deserializer[Article] = PlayJsonDeserializer[Article]
  valueDeSerializer.configure(serde, false)

  private val consumer = new KafkaConsumer(consumerConfig, keyDeSerializer, valueDeSerializer)

  consumer.subscribe(asJavaCollection(List(topic)))

  consumer.seekToBeginning(Nil.asJava)

  Try {
    while (true) {
      val messages = pool(consumer, FiniteDuration(1, MILLISECONDS))

      for ((_, article) <- messages) {
        logger.info(
          s"New article received. Title: ${article.title} .  Author: ${article.author.name}, Date: ${article.created}  "
        )
      }
    }
  }.recover { case error =>
    logger.error(
      "Something went wrong when seeking messages from begining. Unsubscribing",
      error
    )
    consumer.unsubscribe();
  }
  consumer.close()
}
