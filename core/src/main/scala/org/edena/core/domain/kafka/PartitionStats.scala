package org.edena.core.domain.kafka

/**
 * Statistics for a single Kafka partition.
 *
 * @param partition
 *   the partition number
 * @param endOffset
 *   the latest offset in the partition (total messages ever written)
 * @param committedOffset
 *   the last committed offset (messages processed)
 * @param pending
 *   number of messages pending (waiting + running, i.e. uncommitted)
 */
case class PartitionStats(
  partition: Int,
  endOffset: Long,
  committedOffset: Long,
  pending: Long
)
