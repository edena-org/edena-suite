package org.edena.core.domain.kafka

/**
 * Aggregated statistics for a Kafka topic.
 *
 * @param topic
 *   the topic name
 * @param totalMessages
 *   total messages ever written (across all partitions)
 * @param totalProcessed
 *   total messages processed successfully (committed, not in DLQ)
 * @param totalRunning
 *   total messages currently being processed
 * @param totalWaiting
 *   total messages waiting to be processed (across all partitions)
 * @param totalFailed
 *   total messages that failed processing (in DLQ)
 * @param partitions
 *   per-partition statistics
 */
case class TopicStats(
  topic: String,
  totalMessages: Long,
  totalProcessed: Long,
  totalRunning: Long,
  totalWaiting: Long,
  totalFailed: Long,
  partitions: Seq[PartitionStats]
)
