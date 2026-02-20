package org.edena.core.domain.kafka

/**
 * Status of a message in the Kafka topic.
 */
sealed trait MessageStatus

object MessageStatus {

  /** Message has been processed successfully (offset < committed offset, not in DLQ) */
  case object Processed extends MessageStatus

  /** Message is waiting to be processed (offset >= committed offset) */
  case object Waiting extends MessageStatus

  /** Message failed processing and was sent to the dead letter queue */
  case object Failed extends MessageStatus
}
