package org.edena.kafka.common

import akka.actor.ActorSystem
import com.google.inject.Inject
import com.typesafe.config.Config
import org.edena.core.domain.kafka.KafkaTopicSettings
import org.edena.core.util.LoggingSupport
import play.api.libs.json.Format

import scala.concurrent.ExecutionContext

/**
 * Factory for creating typed Kafka consumers.
 */
trait KafkaConsumerFactory {

  /**
   * Create a new Kafka consumer for the given type and settings.
   *
   * @param settings the topic settings (consumerParallelism, consumerPartitionParallelism)
   * @tparam T       the message type, must have implicit Format[T]
   * @return a new KafkaConsumer instance
   */
  def create[T: Format](settings: KafkaTopicSettings): KafkaConsumer[T]
}

class KafkaConsumerFactoryImpl @Inject()(
  config: Config
)(implicit
  system: ActorSystem,
  ec: ExecutionContext
) extends KafkaConsumerFactory with LoggingSupport {

  override def create[T: Format](
    settings: KafkaTopicSettings
  ): KafkaConsumer[T] = {
    logger.info(
      s"Creating Kafka consumer for topic '${settings.topic}' " +
      s"(parallelism=${settings.consumerParallelism}, " +
      s"partitionParallelism=${settings.consumerPartitionParallelism.getOrElse("auto")})"
    )

    new KafkaConsumerImpl[T](settings, config)
  }
}
