package org.edena.kafka.examples

import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.edena.kafka.common.{ClientConfig, ConsumerUtils}
import org.edena.kafka.serializer.JsonStringDeserializer

import java.util.concurrent.TimeUnit.SECONDS
import scala.collection.JavaConverters.asJavaCollection
import scala.concurrent.duration.FiniteDuration

object ArticleJsonStringConsumer
  extends App
  with ConsumerUtils[Article]
  with ClientConfig
  with JsonStringDeserializer[Article] {

  private val fullConfig = ConfigFactory.load()
  private val consumerConfig = fullConfig.getConfig("kafka.consumer").asJavaMap
  private val topic = "scala-articles"

  private val consumer = new KafkaConsumer(consumerConfig, stringDeserializer, stringDeserializer)

  consumer.subscribe(asJavaCollection(List(topic)))

  while (true) {
    val messages = pool(consumer, FiniteDuration(1, SECONDS))
    for ((_, value) <- messages) {
      val article = fromJsonString(value)
      logger.info(
        s"New article received. Title: ${article.title}. Author: ${article.author.name} "
      )
    }
    consumer.commitAsync()
  }

}
