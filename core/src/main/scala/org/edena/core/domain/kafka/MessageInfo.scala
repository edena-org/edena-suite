package org.edena.core.domain.kafka

import java.util.UUID

/**
 * Information about a specific message found in a Kafka topic.
 *
 * @param key
 *   the message key (UUID)
 * @param partition
 *   the partition where the message is stored
 * @param offset
 *   the offset of the message within the partition
 * @param status
 *   whether the message has been processed or is waiting
 * @param value
 *   the message content
 */
case class MessageInfo[T](
  key: UUID,
  partition: Int,
  offset: Long,
  status: MessageStatus,
  value: T
)
