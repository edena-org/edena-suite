# Kafka Integration Module

Production-ready Kafka integration for the Edena Suite with type-safe serialization, automatic schema generation, and Akka Streams consumers.

## Quick Start

### 1. Start Infrastructure

```bash
cd src/main/resources
sudo docker compose up -d
```

This starts:
- **Kafka Broker**: localhost:9094
- **Schema Registry**: localhost:8081 (Confluent JSON Schema Registry)
- **Kafka UI**: localhost:8088 (web interface)

Verify services are running:
```bash
sudo docker compose ps
```

### 2. Run Producer Example

```bash
sbt "kafka/runMain org.edena.kafka.examples.UserPlayJsonProducer"
```

This will:
- Create a topic `users-test` with 4 partitions
- Generate 1000 user records
- Publish them transactionally with automatic JSON schema registration

### 3. Run Consumer Example

```bash
sbt "kafka/runMain org.edena.kafka.examples.AkkaKafkaUserRobustConsumerExample"
```

This demonstrates:
- Partition-aware Akka Streams consumption
- Schema registry deserialization
- Error handling with Dead Letter Queue

## Features

### Automatic JSON Schema Generation

The `PlayJsonSchemaSerializer` automatically generates JSON schemas from Scala case classes using reflection:

```scala
// Schema is automatically generated and registered
val serializer = new PlayJsonSchemaSerializer[User]
serializer.configure(serdeConfig, isKey = false)

// Just use it - no manual schema definition needed!
producer.send(new ProducerRecord(topic, userId, user))
```

**Benefits:**
- No manual schema files
- Type-safe at compile time
- Automatic schema evolution
- Confluent Schema Registry integration

### Partitioning Strategy

Configure partitions for parallel processing:

```scala
createTopic(
  topic = "users-test",
  numPartitions = 4,        // Scale consumers horizontally
  replicationFactor = 1     // Data redundancy (increase for production)
)
```

### Transactional Producers

Exactly-once semantics with transactional writes:

```scala
producer.initTransactions()
producer.beginTransaction()
try {
  users.foreach(user => produce(producer, topic, user.id, user))
  producer.commitTransaction()
} catch {
  case ex: Exception => producer.abortTransaction()
}
```

### Production-Ready Consumers

Akka Streams consumers with:
- **Partition-aware processing** - Independent processing per partition
- **Configurable parallelism** - Tune throughput per partition
- **Manual offset commits** - Control when offsets are committed
- **DLQ support** - Route failed messages to dead letter queue
- **Graceful shutdown** - Coordinated termination

## Serialization Options

### 1. JSON String (Basic)
```scala
new JsonStringSerializer[Article]
new JsonStringDeserializer[Article](classOf[Article])
```

### 2. Play JSON (Type-Safe)
```scala
new PlayJsonSerializer[Article]  // Requires implicit Writes[Article]
new PlayJsonDeserializer[Article] // Requires implicit Reads[Article]
```

### 3. Play JSON + Schema Registry (Production)
```scala
new PlayJsonSchemaSerializer[User]      // Auto-generates schema
new PlayJsonSchemaDeserializer[User]    // Validates against schema
```

## Configuration

Key settings in `application.conf`:

```hocon
kafka {
  producer {
    bootstrap.servers = "localhost:9094"
    transactional.id.prefix = "edena-scala-kafka-producer"
  }

  serde {
    schema.registry.url = "http://localhost:8081"
    auto.register.schemas = true           # Auto-register new schemas
    use.latest.version = true              # Use latest schema version
    json.fail.invalid.schema = true        # Fail on invalid schemas
  }

  consumer {
    group.id = edena-scala-kafka-consumer
    bootstrap.servers = "localhost:9094"
  }
}

akka.kafka.committer {
  max-batch = 20        # Max messages per commit
  parallelism = 3       # Parallel commit operations
  max-interval = 5s     # Max time between commits
}
```

## Examples

### Producer Examples
- `ArticlePlayJsonProducer` - Publish articles without schema registry
- `UserPlayJsonProducer` - Publish users with automatic schema registration

### Consumer Examples
- `ArticleJsonStringConsumer` - Basic naive consumer
- `ArticlePlayJsonConsumer` - Type-safe Play JSON consumer
- `AkkaKafkaArticleConsumerExample` - Basic Akka Streams consumer
- `AkkaKafkaArticleRobustConsumerExample` - With error handling and DLQ
- `AkkaKafkaUserRobustConsumerExample` - With schema registry

## Architecture

```
Producer → [Schema Registry] → Kafka Topic (4 partitions)
                                     ↓
                              Akka Streams Consumer
                                     ↓
                         [Per-Partition Processing]
                                     ↓
                              Offset Commit
```

## Dependencies

- **Akka Streams Kafka**: Production-grade streaming
- **Play JSON**: Type-safe JSON serialization
- **Confluent Schema Registry Client**: Schema management
- **Edena JSON module**: Automatic schema generation from case classes

## Further Documentation

See `CLAUDE.md` for detailed architecture, patterns, and implementation details.
