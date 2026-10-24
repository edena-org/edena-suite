# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Edena Suite is a comprehensive data discovery and analytics platform built with Scala and Play Framework. It's designed for data exploration, statistical analysis, and machine learning workflows with support for multiple storage backends and ML frameworks.

## Architecture

### Multi-Module Structure
- **ada-web**: Play Framework web application (main UI)
- **ada-server**: Core business logic and services
- **core**: Foundational utilities and base classes
- **json**: JSON processing utilities
- **play**: Play Framework extensions
- **ws**: Web service client utilities
- **elastic/elastic-json**: Elasticsearch integration
- **mongo**: MongoDB integration
- **ignite**: Apache Ignite in-memory data grid
- **kafka**: Apache Kafka producer/consumer with Schema Registry support
- **store-json**: Unified JSON storage abstraction
- **scripting**: GraalVM-based JavaScript and Python script execution
- **ml-spark**: Apache Spark ML integration
- **ml-dl4j**: Deep learning with DL4J
- **elastic-util**: Additional Elasticsearch utilities

### Key Dependencies
- **Scala**: 2.13.16
- **Play Framework**: 2.9.6
- **Apache Spark**: 4.0.4 (runs on JDK 17 and 21; needs Spark's `--add-opens` JVM options, see CHANGELOG)
- **MongoDB**: ReactiveMongo 1.1.0-RC12
- **Elasticsearch**: Elastic4S 8.19.1 (ES 8.x compatible, Akka 2.6.x, Play JSON 2.10.x, Jackson overridden to 2.18.x)
- **Apache Ignite**: 2.17.0
- **GraalVM**: For polyglot scripting (JavaScript, Python)
- **Akka**: 2.6.21

## Common Development Commands

### Build and Compile
```bash
sbt compile           # Compile all modules
sbt adaWeb/compile    # Compile specific module
sbt publishLocal      # Publish to the local Ivy repository
```

### Testing
```bash
sbt test                    # Run all tests
sbt core/test              # Run tests for specific module
sbt adaWeb/test            # Run web app tests
sbt scripting/test         # Run scripting module tests
sbt "testOnly *ClassName*" # Run specific test class
sbt "scripting/testOnly *GraalJSPoolTest*" # Run JavaScript pool tests
```

Use sbt project IDs in commands, not directory names: `adaWeb` (`ada-web`), `adaServer`
(`ada-server`), `elasticJson` (`elastic-json`), `storeJson` (`store-json`), `mlSpark`
(`ml-spark`), `mlDl4j` (`ml-dl4j`), and `elasticUtil` (`elastic-util`). Run `sbt projects`
to list them. Individual modules disable test aggregation, so explicitly select each
module whose tests matter.

Focused field/storage checks:
```bash
sbt "core/testOnly *ContainsWordsCriterionSpec" \
    "mongo/testOnly *MongoRegexSpec" \
    "adaServer/testOnly field.* *StringTextConversionTest *DataSetStoreUpdateTest *SynapseNewFieldMappingTest"
sbt "elasticJson/testOnly *ElasticTextFieldTest *NestedOmniSearchTest" \
    "elastic/testOnly *ElasticStoredTextProjectionTest *OmniSearchTest"
```
The second command requires the local Elasticsearch test service.

#### Full-suite run requirements
A clean single-shot `sbt test` needs a larger heap and some external services/config — the defaults will fail:
- **Heap**: run with `SBT_OPTS="-Xmx6g -Xss8m"`. The default 1 GB heap OOMs during the GraalPy pool init in the `scripting` tests (and the resulting GC thrash can also abort in-flight Elastic tests).
- **Elasticsearch** at `localhost:9200` (creds via `ELASTIC_USERNAME` / `ELASTIC_PASSWORD`) for `elastic` / `elastic-json` tests. Note `ElasticKnnSearchTest` may abort in a full run due to index-state ordering (its `beforeAll` deletes `test_articles_knn` before it exists) — it passes when run standalone via `testOnly *ElasticKnnSearchTest*`.
- **MongoDB** at `localhost:27017` plus `ADA_MONGO_DB_URI` (e.g. `mongodb://localhost:27017/ada-test`) for the ada-web Mongo/importer specs.
- The ada-web `*ImporterSpec` / `AuthControllerTest` / `MongoCrudStoreSpec` additionally need a full Play app + pac4j auth config to build the Guice injector (`SecurityComponents` binding); without it they abort regardless of Mongo. These are known pre-existing harness-config gaps, not code failures.

### Running the Application
```bash
sbt adaWeb/run             # Run the web application (default port 9000)
sbt "adaWeb/run 8080"       # Run on specific port
```

### Development Tools
```bash
sbt clean                  # Clean build artifacts
sbt adaWeb/assets          # Compile web assets
sbt reload                 # Reload build configuration
```

## Important Configuration Files

### Main Configuration
- `ada-web/conf/application.conf`: Main Play app configuration
- `ada-web/conf/core/`: Module-specific configurations
  - `auth.conf`: Authentication settings
  - `data-access.conf`: Database connections
  - `spark-ml.conf`: Spark ML settings
  - `deadbolt.conf`: Security configuration

### Build Configuration
- `build.sbt`: Root project configuration
- `project/Dependencies.scala`: Centralized dependency management
- `project/build.properties`: SBT version (1.9.6)

## Development Guidelines

### Module Dependencies
The project follows a clear dependency hierarchy:
- `ada-web` depends on `play`, `ada-server`, and `scripting`; it also aggregates several independent modules.
- `ada-server` depends on `elastic-json`, `mongo`, `ignite`, and `ml-spark`.
- `elastic-json` depends on `elastic` and `store-json`; `mongo` depends on `store-json`.
- `store-json`, `ignite`, `scripting`, `kafka`, and `play` build on `json`, which depends on `core`.
- `ml-spark`, `ml-dl4j`, `elastic`, and the deprecated `ws` module depend on `core`; `elastic-util` depends on `ws`.
- Shared dependency versions live in `project/Dependencies.scala`; module-specific dependencies live in each module's `build.sbt`.

### Dataset and Field Flow
- Shared field identifiers/specifications and query criteria live in `core`.
- Ada's domain models, field parsing/inference, importers, transformations, and statistics live in `ada-server`.
- `DataSetAccessor` ties together a dataset's dictionary, filters, views, settings, and data store. Recreating its store wrapper does not itself change an existing Elasticsearch mapping.
- Data records use the JSON CRUD store abstraction. Storage adapters implement criteria, projections, and serialization; the Play UI uses these through Ada services/controllers.
- Most web models are presentation-specific; the dataset domain models are in `ada-server/src/main/scala/org/edena/ada/server/models/`.

### Text Fields
- `FieldTypeId.Text` carries the same scalar/array values as `String`. It is serialized by name and was appended to the enum to preserve existing ordinals.
- Inference considers Text after numeric/date/JSON/Enum types and before String. The default threshold is a longest value of 50 characters, configurable per import/inference transformation.
- Elasticsearch maps Text as native `text` with `store: true` (no `keyword`, no sub-field), so projections return values of any length from stored fields, never `_source`. Text has no doc values: it can't be sorted or aggregated (the tables and the records list make Text columns unsortable, and they and the exports ignore such an order, looked up in the dictionary: `DataSetViewHelper.sortableOrderBy`). Widgets with a Text field skip the repo-based generation (`WidgetGenerationService`): the calculators compute them from all the values (full data or streamed).
- Text supports only `contains` (analyzed words; Mongo/cache: case-insensitive substrings), `is defined` and `is undefined`. Other comparisons are rejected in `FieldUtil.toCriterion` and in `ElasticJsonCrudStoreImpl.toSimpleQuery`, not approximated on tokens.
- String <-> Text changes the Elasticsearch mapping type, which can't be done in place: `convertStringAndTextFields` refuses it for Elasticsearch data sets (use the Change field types transformation); Mongo/cache convert in place. Indices from 1.2.0 (keyword + `<field>.text`) must be rebuilt.
- `reindex` fails if any document wasn't copied; never delete a source index based on a reindex or a copy without checking the target's (refreshed) count. Bulk `save`/`update` fail if any item failed (`checkBulkError`): Elasticsearch answers 200 with per-item errors.
- Omni search `criterion` is a scoring alternative that can widen results. Use `requiredCriterion` for restrictions that must apply to every hit, including kNN hits.

### Testing Strategy
- Library tests are located in `src/test/scala/`; the Play application's tests are in `ada-web/test/scala/`.
- Uses ScalaTest 3.2.19 framework (3.1+ style traits: `org.scalatest.flatspec.AnyFlatSpec`, `org.scalatest.matchers.should.Matchers`, etc.)
- ScalaTestPlus Play 6.0.2 for web application testing
- Test resources in `src/test/resources/`

### Data Storage
- **MongoDB**: Primary document storage with ReactiveMongo
- **Elasticsearch**: Full-text search and document indexing
- **Apache Ignite**: Distributed caching and fast data access. Known issue: cached JSON data sets (`cacheDataSet = true`) can't store rows since the Ignite 2.14 upgrade, as the cache's index type validation rejects `_id` (a JSON object, declared `Option`) and numbers (`BigDecimal`, declared `Long`); see `JsonBinaryCacheWrappingCrudStore`. Ignite needs its `--add-opens` JVM flags on JDK 17, so it can't start in the (non-forked) ada-server tests
- **Apache Kafka**: Event streaming with Akka Streams consumers, Schema Registry serialization, and DLQ support
- Unified JSON storage interface for backend flexibility

### Machine Learning
- **Apache Spark**: Large-scale ML algorithms and distributed processing
- **DL4J**: Deep learning and neural networks
- **Breeze**: Linear algebra and numerical computing
- Located in `ml-spark` and `ml-dl4j` modules

### Scripting Engine
- **GraalVM Polyglot**: Multi-language script execution
- **JavaScript Support**: Full ES6+ support with proper variable isolation
- **Python Support**: Python script execution via GraalPy
- **Thread-Safe Pools**: Concurrent script execution with configurable pool sizes
- **Variable Isolation**: Proper cleanup between script executions (const/let/var)
- **Error Handling**: Preserves original PolyglotException for debugging
- **JSON Integration**: Built-in JSON parsing and generation support
- **Async Support**: JavaScript async/await functionality
- **Configuration**: `graalvm.js.pool_size` and `graalvm.js.reset_after_each_use`
- **Testing**: Comprehensive test coverage for concurrent execution and variable cleanup

### Security
- **Deadbolt**: Role-based access control
- **PAC4J**: Multi-provider authentication (LDAP, OIDC)
- **Play Security**: Session management and CSRF protection
- Configuration in `conf/core/auth.conf` and `conf/core/deadbolt.conf`

## Web Application Structure

### Controllers
- Located in `ada-web/app/org/edena/ada/web/controllers/`
- Follow Play Framework conventions
- Use dependency injection with Guice
- Security implemented via Deadbolt annotations

### Views
- Scala HTML templates in `ada-web/app/views/`
- Organized by feature area (dataset, classification, regression, etc.)
- Bootstrap-based responsive design
- JavaScript integration for interactive features

### Models
- Domain models in `ada-web/app/org/edena/ada/web/models/`
- JSON serialization support
- Integration with storage backends

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
- Role-based permissions
- User profiles and settings
- Multi-tenancy support

## Environment Setup

### Required Services
- **MongoDB**: Document storage (default: localhost:27017)
- **Elasticsearch**: Search and indexing (default: localhost:9200)
- **Apache Spark**: ML processing (local mode by default)

### Development Environment
- **JDK**: Java 17 or 21 (Spark 4 requires 17+)
- **SBT**: 1.9.6 or higher
- **Node.js**: For frontend asset compilation
- **Git**: Version control

## Troubleshooting

### Common Issues
- **Out of Memory**: Increase JVM heap size with `-Xmx` settings
- **Port Conflicts**: Use `sbt "adaWeb/run 8080"` for alternative port
- **Database Connection**: Check MongoDB/Elasticsearch service status
- **Compilation Errors**: Run `sbt clean` before rebuild

### Performance Optimization
- **Caching**: Apache Ignite for distributed caching
- **Database Indexing**: Ensure proper MongoDB/Elasticsearch indexes
- **Spark Configuration**: Adjust executor memory and cores for ML tasks
- **Play Configuration**: Tune connection pools and timeout settings
