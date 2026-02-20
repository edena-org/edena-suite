package org.edena.core.domain.kafka

import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits._

/**
 * Configuration settings for a Kafka topic, used by both producer and consumer.
 *
 * @param topic
 *   the Kafka topic name
 * @param dlqTopic
 *   optional dead letter queue topic for failed messages
 * @param numPartitions
 *   number of partitions for topic creation (producer)
 * @param replicationFactor
 *   replication factor for topic creation (producer)
 * @param transactionalEnabled
 *   whether to enable transactional sends (producer)
 * @param consumerParallelism
 *   number of parallel processing threads (consumer)
 * @param consumerPartitionParallelism
 *   optional parallelism per partition, auto-detected if not specified (consumer)
 */
case class KafkaTopicSettings(
  topic: String,
  dlqTopic: Option[String] = None,
  numPartitions: Int = 4,
  replicationFactor: Short = 1,
  transactionalEnabled: Boolean = false,
  consumerParallelism: Int = 1,
  consumerPartitionParallelism: Option[Int] = None
)

object KafkaTopicSettings {
  def fromConfig(config: Config, configPath: String): KafkaTopicSettings = {
    val topicConfig = config.getConfig(configPath)

    KafkaTopicSettings(
      topic = topicConfig.getString("name"),
      dlqTopic = topicConfig.optionalString("dlq"),
      numPartitions = topicConfig.optionalInt("partitions").getOrElse(2),
      replicationFactor = topicConfig.optionalInt("replication-factor").getOrElse(1).toShort,
      transactionalEnabled = topicConfig.optionalBoolean("transactional").getOrElse(false),
      consumerParallelism = topicConfig.optionalInt("consumer-parallelism").getOrElse(1),
      consumerPartitionParallelism = topicConfig.optionalInt("consumer-partition-parallelism")
    )
  }
}
