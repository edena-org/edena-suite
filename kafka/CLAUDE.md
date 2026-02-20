# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kafka integration module for the Edena Suite, providing type-safe Kafka producers and consumers with multiple serialization strategies. The module demonstrates production-ready patterns for event streaming with Scala and Akka Streams.

## Build & Run Commands

- **Build project**: `sbt kafka/compile`
- **Run producer example**: `sbt "kafka/runMain org.edena.kafka.examples.UserPlayJsonProducer"`
- **Run consumer example**: `sbt "kafka/runMain org.edena.kafka.examples.AkkaKafkaUserRobustConsumerExample"`
- **Format code**: `sbt kafka/scalafmt`
- **Clean build**: `sbt kafka/clean`

## Infrastructure Setup

The project requires a Kafka cluster with schema registry. Start infrastructure:

```bash
cd kafka/src/main/resources
sudo docker compose up -d
```

Services:
- **Kafka broker**: localhost:9094
- **Schema Registry**: localhost:8081 (Confluent Schema Registry for JSON Schema)
- **Kafka UI**: localhost:8088

## Architecture

### Core Data Models
- `Article` case class with nested `Author` - blog article domain object
- `User` case class - user profile domain object
- Located in `org.edena.kafka.examples`

### Serialization Strategies

#### 1. JSON String Serialization
- **Serializer**: `JsonStringSerializer`
- **Deserializer**: `JsonStringDeserializer`
- Manual JSON handling with Jackson
- No schema registry integration
- Simplest approach for basic use cases

#### 2. Play JSON Serialization
- **Serializer**: `PlayJsonSerializer`
- **Deserializer**: `PlayJsonDeserializer`
- Type-safe JSON with Play JSON implicit formats
- No schema registry integration
- Uses `JsonFormats` trait for format definitions

#### 3. Play JSON with Schema Registry
- **Serializer**: `PlayJsonSchemaSerializer[T: Writes: TypeTag]`
- **Deserializer**: `PlayJsonSchemaDeserializer[T: Reads]`
- **Automatic schema generation** from Scala case classes using reflection
- Schema registration with Confluent Schema Registry
- Wire format: `[magic_byte(0x00)][schema_id(4 bytes)][json_payload]`
- Leverages `JsonSchemaReflectionHelper` from `json` module
- Best for production with schema evolution requirements

### Producer Patterns

#### Transactional Producers
Located in `org.edena.kafka.examples`:
- `ArticlePlayJsonProducer` - Article publishing with Play JSON
- `UserPlayJsonProducer` - User publishing with schema registry

Features:
- Transactional writes with `initTransactions()` / `beginTransaction()` / `commitTransaction()`
- Automatic topic creation with configurable partitions and replication
- Unique transactional IDs per producer instance
- Error handling with transaction abort
- Batch generation via `Generator.users` and `Generator.article`

Example from `UserPlayJsonProducer`:
```scala
val valueSerializer = new PlayJsonSchemaSerializer[User]
valueSerializer.configure(serde, false)

createTopic(topic, numPartitions = 4, replicationFactor = 1, producerConfig)
producer.initTransactions()
producer.beginTransaction()
users.foreach(user => produce(producer, topic, user.id, user))
producer.commitTransaction()
```

### Consumer Patterns

#### 1. Naive Consumers (`NaiveKafkaConsumer`)
- Simple polling-based consumers
- Examples: `ArticleJsonStringConsumer`, `ArticlePlayJsonConsumer`
- Synchronous processing
- Manual partition assignment
- Best for testing and simple use cases

#### 2. Async Consumers (`KafkaAsyncConsumer`)
- Asynchronous message processing with `Future`
- Automatic offset management
- Configurable batch processing
- More efficient than naive consumers

#### 3. Akka Streams Consumers (`AkkaKafkaConsumer`)
Production-ready consumer with:
- **Partition-aware processing** with `committablePartitionedSource`
- **Configurable parallelism** per partition
- **Manual offset commits** via Akka Kafka committer
- **Dead Letter Queue (DLQ)** support for failed messages
- **Graceful shutdown** with coordinated termination
- **Error handling** with retry logic

Example consumers:
- `AkkaKafkaArticleConsumerExample` - Basic processing
- `AkkaKafkaArticleRobustConsumerExample` - With DLQ and error handling
- `AkkaKafkaUserRobustConsumerExample` - User processing with schema registry

### Configuration

#### Main Configuration (`application.conf`)
```hocon
kafka {
  producer {
    client.id = edena-scala-kafka-producer
    bootstrap.servers = "localhost:9094"
    transactional.id.prefix = "edena-scala-kafka-producer"
  }

  serde {
    schema.registry.url = "http://localhost:8081"
    auto.register.schemas = true
    use.latest.version = true
    json.fail.invalid.schema = true
  }

  consumer {
    group.id = edena-scala-kafka-consumer
    bootstrap.servers = "localhost:9094"
  }
}

akka.kafka.committer {
  max-batch = 20
  parallelism = 3
  max-interval = 5s
}
```

### Common Utilities

#### `ProducerUtils` trait
- `produce()` - Send records with callback logging
- `createTopic()` - Create topics with partition/replication settings
- `getTransactionalProducerConfig()` - Generate unique transactional configs

#### `ClientConfig` trait
- Configuration loading and mapping
- Producer/consumer config extraction

### Schema Management

#### Automatic Schema Generation
The `PlayJsonSchemaSerializer` uses `JsonSchemaReflectionHelper` to:
1. Generate JSON Schema from Scala case class type (using TypeTag)
2. Register schema with Confluent Schema Registry
3. Embed schema ID in message wire format
4. Enable schema evolution and compatibility checks

Benefits:
- No manual schema definition required
- Type-safe at compile time
- Schema evolution support via registry
- Compatible with Confluent ecosystem

### Partitioning Strategy

Topics are created with configurable partitioning:
```scala
createTopic(
  topic = "users-test",
  numPartitions = 4,        // Parallel processing capability
  replicationFactor = 1     // Data redundancy
)
```

Consumers process partitions independently for horizontal scalability.

## Development Notes

- **Scala Version**: 2.13.11 (compatible with Edena Suite)
- **Akka Streams**: Production-grade stream processing
- **Play JSON**: Type-safe JSON serialization
- **Confluent Schema Registry**: JSON Schema management
- **Transactional Semantics**: Exactly-once delivery guarantees
- All examples are runnable applications (`extends App`)