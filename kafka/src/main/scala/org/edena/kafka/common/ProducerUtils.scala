package org.edena.kafka.common

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.apache.kafka.clients.producer.{
  Callback,
  KafkaProducer,
  ProducerRecord,
  RecordMetadata
}
import org.apache.kafka.common.errors.TopicExistsException
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.{IterableHasAsJava, MapHasAsJava}
import scala.util.Try

trait ProducerUtils extends ClientConfig {

  protected val logger = Logger(LoggerFactory.getLogger(this.getClass))

  implicit val callback: Callback = (
    metadata: RecordMetadata,
    exception: Exception
  ) =>
    Option(exception)
      .map(error => logger.error("fail to send record due to: ", error))
      .getOrElse(
        logger.info(
          s"Successfully produced a new record to kafka: ${s"topic: ${metadata.topic}, partition: ${metadata.partition}, offset: ${metadata.offset}, key size: ${metadata.serializedKeySize}, value size: ${metadata.serializedKeySize}"}"
        )
      )

  def produce[K, V](
    producer: KafkaProducer[K, V],
    topic: String,
    key: K,
    value: V
  ): Unit = {
    val record = new ProducerRecord(topic, key, value)
    producer.send(record, implicitly[Callback])
  }

  def createTopic(
    topic: String,
    numPartitions: Int,
    replicationFactor: Short,
    producerConfig: java.util.Map[String, AnyRef]
  ): Unit = {
    val adminClient = AdminClient.create(producerConfig)
    Try {
      val newTopic = new NewTopic(topic, numPartitions, replicationFactor)
      adminClient.createTopics(Seq(newTopic).asJavaCollection).all().get()
      logger.info(s"Created topic $topic with $numPartitions partitions and $replicationFactor replication factor")
    }.recover { case ex: TopicExistsException =>
      logger.info(s"Topic $topic already exists. Skipping creation.")
    }
    adminClient.close()
  }

  def getTransactionalProducerConfig(
    config: Config,
    producerConfigName: String
  ): java.util.Map[String, AnyRef] = {
    val producerConfigAux = config.getConfig(producerConfigName).toMap
    val transactionalIdPrefix = producerConfigAux("transactional.id.prefix").asInstanceOf[String]
    val producerConfig = producerConfigAux + ((
      "transactional.id",
      s"$transactionalIdPrefix-${java.util.UUID.randomUUID()}"
    ))
    producerConfig.asJava
  }
}
