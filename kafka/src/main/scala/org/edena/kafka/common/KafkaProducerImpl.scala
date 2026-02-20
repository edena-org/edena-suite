package org.edena.kafka.common

import com.typesafe.config.Config
import org.apache.kafka.clients.producer.{Callback, ProducerConfig, ProducerRecord, RecordMetadata, KafkaProducer => ApacheKafkaProducer}
import org.apache.kafka.common.serialization.{Serializer, UUIDSerializer}
import org.edena.core.domain.kafka.KafkaTopicSettings
import org.edena.core.util.LoggingSupport
import play.api.libs.json.Format

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * Implementation of KafkaProducer using edena-kafka library with Play JSON serialization.
 *
 * @param settings
 *   topic settings (topic, partitions, replication, transactional)
 * @param config
 *   Typesafe config containing kafka.producer and kafka.serde sections
 * @param ec
 *   execution context for async operations
 * @tparam T
 *   the type of messages, must have implicit Format[T]
 */
private[kafka] class KafkaProducerImpl[T: Format](
  settings: KafkaTopicSettings,
  config: Config,
  valueSerializer: Serializer[T]
)(
  implicit ec: ExecutionContext
) extends KafkaProducer[T]
    with ClientConfig
    with ProducerUtils
    with LoggingSupport {

  import settings.{numPartitions, replicationFactor, transactionalEnabled}

  override val topic: String = settings.topic

  // Resolve logger conflict between ProducerUtils and LoggingSupport
  override protected val logger: com.typesafe.scalalogging.Logger =
    com.typesafe.scalalogging.Logger(getClass)

  private val producerConfig: java.util.Map[String, AnyRef] = {
    val baseConfig = getTransactionalProducerConfig(config, "kafka.producer")

    // Make client.id unique per topic to avoid JMX MBean registration conflicts
    // when multiple producers are created (e.g., for doc-upload and restated-version topics)
    val configWithUniqueClientId = new java.util.HashMap[String, AnyRef](baseConfig)
    val baseClientId = configWithUniqueClientId.getOrDefault(ProducerConfig.CLIENT_ID_CONFIG, "kafka-producer").toString
    configWithUniqueClientId.put(ProducerConfig.CLIENT_ID_CONFIG, s"$baseClientId-$topic")

    if (transactionalEnabled) {
      configWithUniqueClientId
    } else {
      // Remove transactional.id when transactions are disabled to avoid
      // "Cannot add partition to transaction before completing initTransactions" error
      configWithUniqueClientId.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG)
      configWithUniqueClientId
    }
  }

  private val keySerializer: Serializer[UUID] = new UUIDSerializer()

  private lazy val producer: ApacheKafkaProducer[UUID, T] = {
    createTopic(topic, numPartitions, replicationFactor, producerConfig)

    val p = new ApacheKafkaProducer[UUID, T](producerConfig, keySerializer, valueSerializer)

    logger.info(s"Kafka producer created for topic '$topic' (partitions=$numPartitions, replication=$replicationFactor)")

    if (transactionalEnabled) {
      p.initTransactions()
      logger.info(s"Transactional producer initialized for topic '$topic'")
    }

    p
  }

  override def send(key: UUID, value: T): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    val record = new ProducerRecord[UUID, T](topic, key, value)

    val callback = new Callback {
      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
        if (exception != null) {
          logger.error(s"Failed to send message with key $key to topic $topic", exception)
          promise.failure(exception)
        } else {
          logger.debug(s"Message sent to $topic partition ${metadata.partition()} offset ${metadata.offset()}")
          promise.success(metadata)
        }
      }
    }

    producer.send(record, callback)
    promise.future
  }

  override def sendBatch(items: Seq[(UUID, T)]): Future[Seq[RecordMetadata]] = {
    Future.sequence(items.map { case (key, value) => send(key, value) })
  }

  override def sendTransactional(items: Seq[(UUID, T)]): Future[Unit] = Future {
    if (!transactionalEnabled) {
      throw new IllegalStateException(
        s"Transactional sends not enabled for topic '$topic'. Set transactionalEnabled=true."
      )
    }

    producer.beginTransaction()
    Try {
      items.foreach { case (key, value) =>
        val record = new ProducerRecord[UUID, T](topic, key, value)
        producer.send(record).get() // Synchronous within transaction
      }
      producer.commitTransaction()
      logger.info(s"Transaction committed: ${items.size} messages to $topic")
    }.recover { case error =>
      logger.error(s"Transaction failed for topic $topic, aborting", error)
      producer.abortTransaction()
      throw error
    }.get
  }

  override def close(): Unit = {
    Try(producer.close()) match {
      case Success(_) => logger.info(s"Kafka producer for topic '$topic' closed")
      case Failure(e) => logger.warn(s"Error closing Kafka producer for topic '$topic': ${e.getMessage}")
    }
  }

  override def ensureTopicExists(): Unit = {
    // Accessing producer triggers lazy initialization which creates the topic
    producer
    ()
  }
}
