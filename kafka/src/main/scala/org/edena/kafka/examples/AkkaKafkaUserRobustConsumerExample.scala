package org.edena.kafka.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.edena.kafka.common.{AkkaKafkaConsumer, ClientConfig}
import org.edena.kafka.examples.JsonFormats._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object AkkaKafkaUserRobustConsumerExample extends App with ClientConfig {
  implicit val system: ActorSystem = ActorSystem("AkkaKafkaUserRobustConsumerExample")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val fullConfig = ConfigFactory.load()
  private val consumerConfig = fullConfig.getConfig("kafka.consumer").toMap
  private val topic = "users-test"
  private val dlqTopic = Some(topic + "-dlq")

  val consumer = AkkaKafkaConsumer.ofUUIDKey[User](
    topics = Seq(topic),
    dlqTopic = dlqTopic,
    config = consumerConfig,
    parallelism = 2
    // partitionParallelism auto-detected from topic
  )

  consumer.run { record =>
    Future {
      val user = record.value
      logger.info(s"User received - partition ${record.partition()}. Name: ${user.name}, Email: ${user.email} ")
      Thread.sleep(100) // Simulate processing time
    }
  }
}
