package org.edena.kafka.common

import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.kafka._
import akka.kafka.scaladsl._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.{
  ConsumerRecord,
  ConsumerConfig => KafkaConsumerConfig
}
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization._
import org.edena.kafka.serializer.{PlayJsonSchemaDeserializer, PlayJsonSerializer}
import org.slf4j.LoggerFactory
import play.api.libs.json.Format

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

private class AkkaKafkaConsumer[K, V: Format](
  topics: Seq[String],
  dlqTopic: Option[String],
  parallelism: Int,
  partitionParallelism: Option[Int],
  keyDeserializer: Deserializer[K],
  keySerializer: Serializer[K],
  config: Map[String, AnyRef] = Map.empty
)(
  implicit system: ActorSystem,
  ec: ExecutionContext
) extends KafkaAsyncConsumer[K, V] with ClientConfig {

//  private val valueDeSerializer: Deserializer[V] = PlayJsonDeserializer[V]
  private val valueDeSerializer: Deserializer[V] = new PlayJsonSchemaDeserializer[V]
  private val valueSerializer: Serializer[V] = PlayJsonSerializer[V]

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val consumerSettings =
    ConsumerSettings(
      system,
      keyDeserializer = keyDeserializer,
      valueDeserializer = valueDeSerializer
    )
      .withProperties(flattenStringMap(config))
      .withProperty(KafkaConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(KafkaConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false") // We'll commit manually

  private val producerSettings =
    ProducerSettings(
      system,
      keySerializer = keySerializer,
      valueSerializer = valueSerializer
    ).withProperties(flattenStringMap(config))

  // DLQ producer created lazily and only if dlqTopic is defined
  // Uses unique client.id suffix to avoid JMX MBean registration conflicts
  private lazy val dlqSink = dlqTopic.map { topic =>
    val baseClientId = config.getOrElse(ProducerConfig.CLIENT_ID_CONFIG, "kafka-producer").toString
    val dlqProducerSettings = producerSettings
      .withProperty(ProducerConfig.CLIENT_ID_CONFIG, s"$baseClientId-$topic")
    val dlqProducer = dlqProducerSettings.createKafkaProducer()
    Producer.plainSink(dlqProducerSettings.withProducer(dlqProducer))
  }

  // uses akka.kafka.committer settings from application.conf
  private val committerSettings = CommitterSettings(system)

  override def run(
    processRecord: ConsumerRecord[K, V] => Future[Unit]
  ): Unit = {
    // Auto-detect partition count if not explicitly set
    val effectivePartitionParallelism = partitionParallelism.getOrElse {
      val start = new Date()
      val adminClient = AdminClient.create(config.asJava)

      val maxPartitions = topics.map { topic =>
        val topicDescription = adminClient.describeTopics(Seq(topic).asJava).allTopicNames().get()
        val partitionCount = topicDescription.get(topic).partitions().size()
        logger.info(s"Auto-detected $partitionCount partitions for topic '$topic' in ${new Date().getTime - start.getTime} ms")
        partitionCount
      }.max

      adminClient.close()

      logger.info(s"Using partitionParallelism = $maxPartitions (auto-detected)")
      maxPartitions
    }

    val partitionedSource =
      Consumer.committablePartitionedSource(
        consumerSettings,
        Subscriptions.topics(topics.toSet)
      )

    val dlqFlow: Flow[CommittableMessage[K, V], CommittableOffset, NotUsed] =
      Flow[CommittableMessage[K, V]]
        .mapAsync(parallelism) { msg =>
          processRecord(msg.record).map(_ =>
            msg.committableOffset
          ).recoverWith {
            case ex => handleException(ex, msg)
          }
        }

    val processingStream: Future[Done] =
      partitionedSource
        .map {
          case (topicPartition, source) =>
            logger.info(s"Processing partition ${topicPartition.partition()}")

            source
              .via(dlqFlow)
              .via(Committer.flow(committerSettings))
        }
        .flatMapMerge(breadth = effectivePartitionParallelism, identity)
        .runWith(Sink.ignore)

    processingStream.onComplete(_ => system.terminate())
  }

  private def handleException(
    ex: Throwable,
    msg: CommittableMessage[K, V]
  ): Future[CommittableOffset] = {
    val record = msg.record

    val resolutionPart = if (dlqTopic.isDefined) {
      s"Pushing to a DLQ topic ${dlqTopic.get}."
    } else {
      "No DQL topic defined, skipping."
    }

    logger.error(
      s"Failed to process Kafka record ${record.key()} of type ${record.value().getClass.getName}. $resolutionPart.",
      ex
    )

    dlqSink.zip(dlqTopic).map { case (sink, topic) =>
      // Produce to DLQ using shared producer
      Source
        .single(new ProducerRecord(topic, record.key(), record.value))
        .runWith(sink)
        .map(_ => msg.committableOffset)
    }.getOrElse(
      Future.successful(msg.committableOffset)
    )
  }
}

object AkkaKafkaConsumer {
  // common key (de)serializers
  private val stringDeserializer = new StringDeserializer
  private val stringSerializer = new StringSerializer
  private val uuidDeserializer = new UUIDDeserializer
  private val uuidSerializer = new UUIDSerializer

  def apply[K, V: Format](
    topics: Seq[String],
    dlqTopic: Option[String],
    keyDeserializer: Deserializer[K],
    keySerializer: Serializer[K],
    config: Map[String, AnyRef],
    parallelism: Int = 1,
    partitionParallelism: Option[Int] = None
  )(
    implicit system: ActorSystem, ec: ExecutionContext
  ): KafkaAsyncConsumer[K, V] =
    new AkkaKafkaConsumer[K, V](topics, dlqTopic, parallelism, partitionParallelism, keyDeserializer, keySerializer, config)

  def ofStringKey[V: Format](
    topics: Seq[String],
    dlqTopic: Option[String],
    config: Map[String, AnyRef],
    parallelism: Int = 1,
    partitionParallelism: Option[Int] = None
  )(
    implicit system: ActorSystem, ec: ExecutionContext
  ): KafkaAsyncConsumer[String, V] =
    apply(topics, dlqTopic, stringDeserializer, stringSerializer, config, parallelism, partitionParallelism)

  def ofUUIDKey[V: Format](
    topics: Seq[String],
    dlqTopic: Option[String],
    config: Map[String, AnyRef],
    parallelism: Int = 1,
    partitionParallelism: Option[Int] = None
  )(
    implicit system: ActorSystem, ec: ExecutionContext
  ): KafkaAsyncConsumer[java.util.UUID, V] =
    apply(topics, dlqTopic, uuidDeserializer, uuidSerializer, config, parallelism, partitionParallelism)
}
