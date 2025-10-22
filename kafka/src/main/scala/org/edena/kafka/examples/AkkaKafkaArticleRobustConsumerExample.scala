package org.edena.kafka.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.edena.kafka.common.{AkkaKafkaConsumer, ClientConfig}
import org.edena.kafka.examples.JsonFormats._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object AkkaKafkaArticleRobustConsumerExample extends App with ClientConfig {
  implicit val system       = ActorSystem("AkkaKafkaArticleRobustConsumerExample")
  implicit val materializer = Materializer
  implicit val ec: ExecutionContext = system.dispatcher

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val fullConfig = ConfigFactory.load()
  private val consumerConfig = fullConfig.getConfig("kafka.consumer").toMap
  private val topic = "scala-articles-avro"

  private val dlqTopic = Some(topic + "-dlq")

  val consumer = AkkaKafkaConsumer.ofStringKey[Article](consumerConfig)
  consumer.run(Seq(topic), dlqTopic) { record =>
    Future {
      val article = record.value
      logger.info(s"Article received. Title: ${article.title} .  Author: ${article.author.name} ")
    }
  }
}
