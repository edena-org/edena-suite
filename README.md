# Edena Suite [![version](https://img.shields.io/badge/version-1.0.0-green.svg)](https://peterbanda.net) [![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Edena Suite is a comprehensive data discovery and analytics platform built with Scala and Play Framework. It's designed for data exploration, statistical analysis, and machine learning workflows with support for multiple storage backends and ML frameworks.

## Architecture Overview

### Global Configuration
- **Scala**: 2.13.11
- **SBT**: 1.9.6
- **Akka**: 2.6.21 (unified across all modules)
- **Jackson**: 2.14.3 (unified JSON processing)
- **Play JSON**: 2.10.6 (unified Play JSON)

## Module Structure

### Core Infra Modules

### - core
Foundation utilities (reflection, akka, locking), calculators, repository interfaces, shared models
- **Akka**: 2.6.21
- **Guice**: 5.1.0
- **ScalaTest**: 3.0.8
- **Commons**: IO, Lang, Math3
- **Logging**: Logback 1.4.14, SLF4J 1.7.26

### - json
JSON processing utilities
- **Jackson**: 2.14.3
- **Play JSON**: 2.10.6
- Depends on: core

### - store-json
Unified JSON storage abstraction
- **Jackson**: 2.14.3
- **Play JSON**: 2.10.6
- Depends on: json

### Storage Modules

### - mongo
MongoDB integration with ReactiveMongo
- **ReactiveMongo**: 1.1.0-RC12
- **ReactiveMongo Play**: 1.1.0.play29-RC12
- **Akka Streams**: 2.6.21
- Depends on: store-json

### - elastic
Elasticsearch integration
- **Elastic4S**: 8.11.0 (ES 8.x compatible)
- **Apache Commons Lang3**: 3.5
- Depends on: core

### - elastic-json
Elasticsearch JSON storage implementation
- **Elastic4S**: 8.11.0
- **Jackson**: 2.14.3
- Depends on: store-json, elastic

### - ignite
Apache Ignite in-memory data grid
- **Apache Ignite**: 2.14.0 (core, spring, indexing, scalar)
- Depends on: json

### - scripting
GraalVM-based JavaScript and Python script execution
- **GraalVM Polyglot**: 23.1.5
- **Thread-Safe Pools**: Concurrent script execution with configurable pool sizes
- **Variable Isolation**: Proper cleanup between script executions (const/let/var)
- **JSON Integration**: Built-in JSON parsing and generation support
- **Async Support**: JavaScript async/await functionality
- Depends on: core

### Machine Learning Modules

### - ml-spark
Apache Spark ML integration and extensions
- **Apache Spark**: 3.5.4 (core, sql, mllib)
- **Breeze**: 2.1.0
- **Tablesaw**: 0.36.0
- Depends on: core

### - ml-dl4j
Deep learning with DL4J
- **Akka**: 2.6.21
- **Jackson**: 2.14.3
- Depends on: core

### Web & Communication Modules

### - ws (deprecated)
Web service client utilities
- **Akka HTTP**: 10.2.10
- **Play WS**: 2.1.11
- **Jackson**: 2.14.3
- Depends on: core

### - play
Play Framework extensions
- **Play Framework**: 2.9.6
- **Jackson**: 2.14.3
- **Play JSON**: 2.10.6
- Depends on: json

### - elastic-util
Additional Elasticsearch utilities
- **Akka HTTP**: 10.2.10
- **Play WS**: 2.1.11
- Depends on: ws

### Application Modules

### - ada-server
Core business logic and services
- **Play WS**: 2.1.11
- **Breeze**: 2.1.0 (linear algebra)
- **T-SNE Java**: v2.5.0
- **LDAP SDK**: 2.3.8
- **Guice**: 5.1.0
- Depends on: elastic-json, mongo, ignite, ml-spark

### - ada-web
Play Framework web application (main UI)
- **Play Framework**: 2.9.6
- **PAC4J**: 12.0.1-PLAY2.9 (authentication)
- **PAC4J OIDC**: 6.2.1
- **Deadbolt**: 2.9.0 (security)
- **Play Mailer**: 9.0.1
- **ReactiveMongo Play**: 1.1.0.play29-RC12
- **Scalaz**: 7.2.36
- **Frontend Libraries**:
  - Bootstrap Select: 1.13.2
  - Plotly.js: 1.54.1
  - Highcharts: 6.2.0
  - D3: 3.5.16
  - jQuery UI: 1.11.1
- Depends on: play, ada-server

## Key Features

### Data Import/Export
- Support for CSV, JSON, REDCap, Synapse, eGaIT, tranSMART
- Configurable data type inference
- Batch processing capabilities
- Export to various formats

### Analytics & Visualization
- Interactive data exploration
- Statistical analysis and correlations
- Machine learning model training and evaluation
- Chart generation with Plotly.js and D3.js

### User Management
- LDAP integration for enterprise authentication
- Role-based permissions with Deadbolt
- User profiles and settings
- Multi-tenancy support

### Storage Backends
- **MongoDB**: Primary document storage with ReactiveMongo
- **Elasticsearch**: Full-text search and document indexing
- **Apache Ignite**: Distributed caching and fast data access
- Unified JSON storage interface for backend flexibility

### Machine Learning
- **Apache Spark**: Large-scale ML algorithms and distributed processing
- **DL4J**: Deep learning and neural networks
- **Breeze**: Linear algebra and numerical computing

### Scripting Engine
- **GraalVM Polyglot**: Multi-language script execution (JavaScript, Python)
- **Thread-Safe Execution**: Concurrent script execution with configurable pool sizes
- **Variable Isolation**: Proper cleanup between script executions (const/let/var)
- **Error Handling**: Preserves original PolyglotException for debugging
- **JSON Integration**: Built-in JSON parsing and generation support
- **Async Support**: JavaScript async/await functionality

## Development

### Build Commands
```bash
sbt compile           # Compile all modules
sbt ada-web/run       # Run the web application
sbt test              # Run all tests
sbt scripting/test    # Run scripting module tests
sbt publishLocal      # Publish to local Maven repo
```

### Required Services
- **MongoDB**: Document storage (default: localhost:27017)
- **Elasticsearch**: Search and indexing (default: localhost:9200)
- **Apache Spark**: ML processing (local mode by default)

## License

The project and all its source code is distributed under the terms of <a href="https://www.apache.org/licenses/LICENSE-2.0.txt">Apache 2.0 license</a>.