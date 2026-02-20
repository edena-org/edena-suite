package org.edena.kafka.common

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.apache.kafka.clients.admin.{AdminClient, AdminClientConfig}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, KafkaConsumer => ApacheKafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.UUIDDeserializer
import org.edena.core.domain.kafka.{KafkaTopicSettings, MessageInfo, MessageStatus, PartitionStats, TopicStats}
import org.edena.core.util.LoggingSupport
import org.edena.kafka.serializer.PlayJsonSchemaDeserializer
import play.api.libs.json.Format

import java.time.Duration
import java.util.{Properties, UUID}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/**
 * Generic Kafka consumer trait for any type T with a Play JSON Format.
 *
 * @tparam T
 *   the type of messages to consume
 */
trait KafkaConsumer[T] {

  /** The topic this consumer is configured for */
  def topic: String

  /** Start consuming messages and process them with the given handler */
  def run(handler: ConsumerRecord[UUID, T] => Future[Unit]): Unit

  /**
   * Get the number of messages waiting to be processed.
   *
   * @return
   *   the total waiting messages across all partitions
   */
  def getWaiting: Future[Long]

  /**
   * Get detailed statistics for the configured topic including per-partition info.
   *
   * @return
   *   topic statistics with waiting count and partition details
   */
  def getStats: Future[TopicStats]

  /**
   * Find a message by its UUID key. Note: This scans the topic from the beginning, which can be slow for large topics.
   *
   * @param key
   *   the UUID key to search for
   * @return
   *   the message info if found, None otherwise
   */
  def findMessage(key: UUID): Future[Option[MessageInfo[T]]]

  /**
   * Load an index of all messages in the topic. Returns (UUID -> (partition, offset, status)) for each message. Used at startup to
   * pre-populate the job tracking map for O(1) lookups. Status is determined by comparing offset against committed offsets (no DLQ check
   * for bulk loading).
   *
   * @return
   *   map of message key to (partition, offset, status)
   */
  def loadIndex(): Future[Map[UUID, (Int, Long, MessageStatus)]]
}

/**
 * Implementation of KafkaConsumer that delegates to AkkaKafkaRecordConsumer.
 *
 * @param settings
 *   topic settings (consumerParallelism, consumerPartitionParallelism)
 * @param config
 *   Typesafe config containing kafka.consumer section
 * @tparam T
 *   the type of messages, must have implicit Format[T]
 */
private[kafka] class KafkaConsumerImpl[T: Format](
  settings: KafkaTopicSettings,
  config: Config
)(
  implicit system: ActorSystem,
  ec: ExecutionContext
) extends KafkaConsumer[T] with ClientConfig with LoggingSupport {

  override val topic: String = settings.topic
  private val dlqTopic: Option[String] = settings.dlqTopic

  private val consumerConfig: Map[String, AnyRef] =
    config.getConfig("kafka.consumer").toMap

  private val consumerGroupId: String =
    consumerConfig.getOrElse("group.id", "default-consumer-group").toString

  private val bootstrapServers: String =
    consumerConfig.getOrElse("bootstrap.servers", "localhost:9092").toString

  private val delegate = AkkaKafkaRecordConsumer.ofUUIDKey[T](
    Seq(topic),
    dlqTopic,
    consumerConfig,
    settings.consumerParallelism,
    settings.consumerPartitionParallelism
  )

  // Shared properties for admin and search consumers
  private lazy val connectionProps: Properties = {
    val props = new Properties()
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props
  }

  private lazy val adminClient: AdminClient = AdminClient.create(connectionProps)

  private def createDLQSearch(): Option[(ApacheKafkaConsumer[UUID, T], Seq[TopicPartition])] =
    dlqTopic.map(dlq => createSearchConsumer(dlq, "dlq-search"))

  private def createSearch(): (ApacheKafkaConsumer[UUID, T], Seq[TopicPartition]) =
    createSearchConsumer(topic, s"search")

  /** Get committed offsets for this topic from the main consumer group */
  private def getCommittedOffsets: Map[TopicPartition, Long] =
    adminClient
      .listConsumerGroupOffsets(consumerGroupId)
      .partitionsToOffsetAndMetadata()
      .get()
      .asScala
      .collect { case (tp, om) if tp.topic() == topic => tp -> om.offset() }
      .toMap

  /** Check if a key exists in the DLQ topic */
  private def isInDlq(key: UUID): Boolean = {
    val search = createDLQSearch()

    logger.info(s"isInDlq($key) - starting DLQ scan")
    search.exists { case (dlqConsumer, dlqPartitions) =>
      val startNanos = System.nanoTime()
      val timeoutMs = 5000L
      val deadlineNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(timeoutMs)

      try {
        dlqConsumer.seekToBeginning(dlqPartitions.asJava)

        val endOffsets: Map[TopicPartition, Long] =
          dlqConsumer.endOffsets(dlqPartitions.asJava).asScala.view.mapValues(_.toLong).toMap

        var found = false
        var searching = true

        // progress / "stuck" detection
        var lastPositions: Map[TopicPartition, Long] = Map.empty
        var idlePolls = 0
        val maxIdlePolls = 3

        while (searching && !found && System.nanoTime() < deadlineNanos) {
          val remainingMs =
            TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()).max(0L)
          val pollMs = Math.min(250L, remainingMs)
          val records = dlqConsumer.poll(Duration.ofMillis(pollMs)).asScala

          if (records.nonEmpty) {
            found = records.exists(_.key() == key)
          }

          if (!found) {
            val positions: Map[TopicPartition, Long] =
              dlqPartitions.iterator.map(tp => tp -> dlqConsumer.position(tp)).toMap

            val reachedEnd =
              positions.forall { case (tp, pos) => pos >= endOffsets.getOrElse(tp, 0L) }

            val progressed =
              positions.exists { case (tp, pos) => !lastPositions.contains(tp) || lastPositions(tp) != pos }

            if (!progressed && records.isEmpty) idlePolls += 1 else idlePolls = 0
            lastPositions = positions

            if (reachedEnd) searching = false
            if (idlePolls >= maxIdlePolls) {
              logger.warn(
                s"isInDlq($key) - no progress for $idlePolls polls; breaking early " +
                  s"(positions=$positions, endOffsets=$endOffsets)"
              )
              searching = false
            }
          }
        }

        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        val timedOut = System.nanoTime() >= deadlineNanos && !found
        if (timedOut) logger.warn(s"isInDlq($key) - scan timed out after ${elapsedMs}ms")
        logger.info(s"isInDlq($key) - DLQ scan completed in ${elapsedMs}ms, found=$found")
        found
      } catch {
        case e: Exception =>
          val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
          logger.error(s"isInDlq($key) - FAILED after ${elapsedMs}ms: ${e.getMessage}", e)
          false
      } finally {
        dlqConsumer.close()
      }
    }
  }

  /** Bulk-load all keys from the DLQ topic. Used by loadIndex() for efficient batch classification. */
  private def loadDlqKeys(): Set[UUID] = {
    val search = createDLQSearch()

    search.map { case (dlqConsumer, dlqPartitions) =>
      logger.info(s"loadDlqKeys() - scanning DLQ for topic '$topic'")
      val startNanos = System.nanoTime()

      try {
        dlqConsumer.seekToBeginning(dlqPartitions.asJava)

        val endOffsets: Map[TopicPartition, Long] =
          dlqConsumer.endOffsets(dlqPartitions.asJava).asScala.view.mapValues(_.toLong).toMap

        val keys = scala.collection.mutable.Set[UUID]()
        var searching = true
        var lastPositions: Map[TopicPartition, Long] = Map.empty
        var idlePolls = 0
        val maxIdlePolls = 3

        while (searching) {
          val records = dlqConsumer.poll(Duration.ofMillis(500)).asScala
          records.foreach(r => keys.add(r.key()))

          val positions: Map[TopicPartition, Long] =
            dlqPartitions.iterator.map(tp => tp -> dlqConsumer.position(tp)).toMap

          val reachedEnd =
            positions.forall { case (tp, pos) => pos >= endOffsets.getOrElse(tp, 0L) }

          val progressed =
            positions.exists { case (tp, pos) => lastPositions.get(tp).forall(_ != pos) }

          if (!progressed && records.isEmpty) idlePolls += 1 else idlePolls = 0
          lastPositions = positions

          if (reachedEnd) searching = false
          if (idlePolls >= maxIdlePolls) {
            logger.warn(s"loadDlqKeys() - no progress for $idlePolls polls; breaking early")
            searching = false
          }
        }

        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        logger.info(s"loadDlqKeys() - DLQ scan completed in ${elapsedMs}ms, found ${keys.size} failed keys")
        keys.toSet
      } catch {
        case e: Exception =>
          val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
          logger.error(s"loadDlqKeys() - FAILED after ${elapsedMs}ms: ${e.getMessage}", e)
          Set.empty[UUID]
      } finally {
        dlqConsumer.close()
      }
    }.getOrElse(Set.empty[UUID])
  }

  /** Get the total number of messages in the DLQ */
  private def getDlqMessageCount: Long = {
    val search = createDLQSearch()

    search.map { case (dlqConsumer, dlqPartitions) =>
      try {
        dlqConsumer.endOffsets(dlqPartitions.asJava).asScala.values.map(_.toLong).sum
      } finally {
        dlqConsumer.close()
      }
    }.getOrElse(0L)
  }

  override def run(handler: ConsumerRecord[UUID, T] => Future[Unit]): Unit = {
    delegate.run(handler)
  }

  override def getWaiting: Future[Long] = getStats.map(_.totalWaiting)

  override def getStats: Future[TopicStats] = Future {
    logger.info(s"getStats() - fetching stats for topic '$topic'")
    val startNanos = System.nanoTime()

    // Get partition info for the topic
    val topicDescription = adminClient
      .describeTopics(java.util.Collections.singletonList(topic))
      .allTopicNames()
      .get()
      .asScala
      .get(topic)

    val partitions = topicDescription
      .map(_.partitions().asScala.map(p => new TopicPartition(topic, p.partition())).toSeq)
      .getOrElse(Seq.empty)

    if (partitions.isEmpty) {
      TopicStats(
        topic,
        totalMessages = 0L,
        totalProcessed = 0L,
        totalRunning = 0L,
        totalWaiting = 0L,
        totalFailed = 0L,
        partitions = Seq.empty
      )
    } else {
      // Get end offsets (latest message position)
      val endOffsets = adminClient
        .listOffsets(partitions.map(tp => tp -> org.apache.kafka.clients.admin.OffsetSpec.latest()).toMap.asJava)
        .all()
        .get()
        .asScala
        .map { case (tp, info) => tp -> info.offset() }
        .toMap

      // Get committed offsets (consumer group progress)
      val committedOffsets = getCommittedOffsets

      val partitionStats = partitions.map { tp =>
        val endOffset = endOffsets.getOrElse(tp, 0L)
        val committedOffset = committedOffsets.getOrElse(tp, 0L)
        val pending = math.max(0L, endOffset - committedOffset)
        PartitionStats(tp.partition(), endOffset, committedOffset, pending)
      }.sortBy(_.partition)

      val totalMessages = partitionStats.map(_.endOffset).sum
      val totalFailed = getDlqMessageCount
      val totalCommitted = partitionStats.map(_.committedOffset).sum
      val totalProcessed = totalCommitted - totalFailed // Successfully processed = committed - failed
      val totalWaiting = partitionStats.map(_.pending).sum

      val stats = TopicStats(topic, totalMessages, totalProcessed, totalRunning = 0L, totalWaiting, totalFailed, partitionStats)
      val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
      logger.info(s"getStats() - topic '$topic' completed in ${elapsedMs}ms [messages=$totalMessages, processed=$totalProcessed, waiting=$totalWaiting, failed=$totalFailed]")
      stats
    }
  }

  override def findMessage(key: UUID): Future[Option[MessageInfo[T]]] = Future {
    val (consumer, partitions) = createSearch()

    logger.info(s"findMessage($key) - starting scan of topic '$topic'")

    val startNanos = System.nanoTime()
    val timeoutMs = 5000L
    val deadlineNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(timeoutMs)

    try {
      // Reset to the beginning for each search
      consumer.seekToBeginning(partitions.asJava)

      // Snapshot end offsets (stop boundary)
      val endOffsets: Map[TopicPartition, Long] =
        consumer.endOffsets(partitions.asJava).asScala.view.mapValues(_.toLong).toMap

      // Snapshot committed offsets (status boundary)
      val committedOffsets = getCommittedOffsets

      var result: Option[MessageInfo[T]] = None
      var searching = true

      // progress / “stuck” detection
      var lastPositions: Map[TopicPartition, Long] = Map.empty
      var idlePolls = 0
      val maxIdlePolls = 3

      while (searching && System.nanoTime() < deadlineNanos) {
        // poll in small slices so timeout is respected tightly
        val remainingMs =
          TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()).max(0L)
        val pollMs = Math.min(250L, remainingMs) // <= 250ms slices
        val records = consumer.poll(Duration.ofMillis(pollMs)).asScala

        // Search fetched records
        records.find(_.key() == key).foreach { record =>
          val tp = new TopicPartition(record.topic(), record.partition())
          val committedOffset = committedOffsets.getOrElse(tp, 0L)

          val status =
            if (record.offset() >= committedOffset) MessageStatus.Waiting
            else if (isInDlq(key)) MessageStatus.Failed
            else MessageStatus.Processed

          result = Some(
            MessageInfo(
              key = record.key(),
              partition = record.partition(),
              offset = record.offset(),
              status = status,
              value = record.value()
            )
          )
          searching = false
        }

        if (searching) {
          // Reached end snapshot?
          val positions: Map[TopicPartition, Long] =
            partitions.iterator.map(tp => tp -> consumer.position(tp)).toMap

          val reachedEnd =
            positions.forall { case (tp, pos) => pos >= endOffsets.getOrElse(tp, 0L) }

          val progressed =
            positions.exists { case (tp, pos) => lastPositions.get(tp).forall(_ != pos) }

          if (!progressed && records.isEmpty) idlePolls += 1 else idlePolls = 0
          lastPositions = positions

          // Stop conditions
          if (reachedEnd) searching = false
          if (idlePolls >= maxIdlePolls) {
            logger.warn(
              s"findMessage($key) - no progress for $idlePolls polls; breaking early " +
                s"(positions=$positions, endOffsets=$endOffsets)"
            )
            searching = false
          }
        }
      }

      val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
      val timedOut = System.nanoTime() >= deadlineNanos && result.isEmpty
      if (timedOut) logger.warn(s"findMessage($key) - scan timed out after ${elapsedMs}ms")
      logger.info(s"findMessage($key) - scan completed in ${elapsedMs}ms, found=${result.isDefined}")

      result
    } catch {
      case e: Exception =>
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        logger.error(s"findMessage($key) - FAILED after ${elapsedMs}ms: ${e.getMessage}", e)
        None
    } finally {
      consumer.close()
    }
  }

  override def loadIndex(): Future[Map[UUID, (Int, Long, MessageStatus)]] = Future {
    val (consumer, partitions) = createSearch()

    logger.info(s"loadIndex() - scanning topic '$topic'...")

    val startNanos = System.nanoTime()

    try {
      consumer.seekToBeginning(partitions.asJava)

      val endOffsets: Map[TopicPartition, Long] =
        consumer.endOffsets(partitions.asJava).asScala.view.mapValues(_.toLong).toMap

      val committedOffsets = getCommittedOffsets
      val dlqKeys = loadDlqKeys()

      val result = scala.collection.mutable.Map[UUID, (Int, Long, MessageStatus)]()
      var searching = true
      var lastPositions: Map[TopicPartition, Long] = Map.empty
      var idlePolls = 0
      val maxIdlePolls = 3

      while (searching) {
        val records = consumer.poll(Duration.ofMillis(500)).asScala

        records.foreach { record =>
          val tp = new TopicPartition(record.topic(), record.partition())
          val committedOffset = committedOffsets.getOrElse(tp, 0L)
          val status =
            if (record.offset() >= committedOffset) MessageStatus.Waiting
            else if (dlqKeys.contains(record.key())) MessageStatus.Failed
            else MessageStatus.Processed
          result.put(record.key(), (record.partition(), record.offset(), status))
        }

        val positions: Map[TopicPartition, Long] =
          partitions.iterator.map(tp => tp -> consumer.position(tp)).toMap

        val reachedEnd =
          positions.forall { case (tp, pos) => pos >= endOffsets.getOrElse(tp, 0L) }

        val progressed =
          positions.exists { case (tp, pos) => lastPositions.get(tp).forall(_ != pos) }

        if (!progressed && records.isEmpty) idlePolls += 1 else idlePolls = 0
        lastPositions = positions

        if (reachedEnd) searching = false
        if (idlePolls >= maxIdlePolls) {
          logger.warn(
            s"loadIndex() - no progress for $idlePolls polls; breaking early " +
              s"(positions=$positions, endOffsets=$endOffsets)"
          )
          searching = false
        }
      }

      val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
      logger.info(s"loadIndex() - scanned topic '$topic' in ${elapsedMs}ms, found ${result.size} messages")
      result.toMap
    } catch {
      case e: Exception =>
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        logger.error(s"loadIndex() - FAILED after ${elapsedMs}ms: ${e.getMessage}", e)
        Map.empty
    } finally {
      consumer.close()
    }
  }

  /** Create a search consumer for a given topic with assigned partitions */
  private def createSearchConsumer(
    topicName: String,
    groupSuffix: String
  ): (ApacheKafkaConsumer[UUID, T], Seq[TopicPartition]) = {
    val props = new Properties()
    props.putAll(connectionProps)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, s"$consumerGroupId-$groupSuffix")
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")

    val format = implicitly[Format[T]]
    val consumer = new ApacheKafkaConsumer[UUID, T](
      props,
      new UUIDDeserializer(),
      new PlayJsonSchemaDeserializer[T]()(format)
    )

    // Assign all partitions once during initialization
    val partitions = consumer.partitionsFor(topicName).asScala
      .map(p => new TopicPartition(topicName, p.partition())).toSeq
    consumer.assign(partitions.asJava)

    (consumer, partitions)
  }

}
