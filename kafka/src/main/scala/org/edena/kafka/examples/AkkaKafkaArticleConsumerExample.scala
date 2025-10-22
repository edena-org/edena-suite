package org.edena.kafka.examples

import akka.Done
import akka.actor.ActorSystem
import akka.kafka._
import akka.kafka.scaladsl._
import akka.stream._
import akka.stream.scaladsl._
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.{ConsumerConfig => KafkaConsumerConfig}
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import org.edena.kafka.common.ClientConfig
import org.edena.kafka.examples.JsonFormats._
import org.edena.kafka.serializer.PlayJsonDeserializer

import scala.concurrent.{ExecutionContext, Future}

object AkkaKafkaArticleConsumerExample extends App with ClientConfig {
  implicit val system       = ActorSystem("AkkaKafkaConsumerExample")
  implicit val materializer = Materializer
  implicit val ec: ExecutionContext = system.dispatcher

  private val fullConfig = ConfigFactory.load()
  private val consumerConfig = fullConfig.getConfig("kafka.consumer").toMap
  private val topic = "scala-articles-avro"

  val valueDeSerializer: Deserializer[Article] = PlayJsonDeserializer[Article]

  val consumerSettings =
    ConsumerSettings(
      system,
      keyDeserializer = new StringDeserializer,
      valueDeserializer = valueDeSerializer
    )
      .withProperties(flattenStringMap(consumerConfig))
      .withProperty(KafkaConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(KafkaConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false") // We'll commit manually

  // uses akka.kafka.committer settings from application.conf
  val committerSettings = CommitterSettings(system)
  val topics = Set(topic)

  val partitionedSource =
    Consumer.committablePartitionedSource(consumerSettings, Subscriptions.topics(topics))

  val processingStream: Future[Done] =
    partitionedSource
      .map {
        case (topicPartition, source) =>
          source
            .mapAsync(parallelism = 1) { msg =>
              // Perform your async processing here
              Future {
                val value = msg.record.value()
                println(s"Partition ${topicPartition.partition()}, Offset ${msg.record.offset()}, Value = ${value}")
              }.map(_ => msg.committableOffset)
            }
            // Instead of .mapAsync(...)(offset => offset.commitScaladsl())
            // we push offsets into the Committer flow:
            .via(Committer.flow(committerSettings))
      }
      .flatMapMerge(breadth = 4, identity)
      .runWith(Sink.ignore)

  sys.addShutdownHook {
    system.terminate()
  }
}

