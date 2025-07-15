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
- **store-json**: Unified JSON storage abstraction
- **ml-spark**: Apache Spark ML integration
- **ml-dl4j**: Deep learning with DL4J
- **elastic-util**: Additional Elasticsearch utilities

### Key Dependencies
- **Scala**: 2.13.11
- **Play Framework**: 2.7.9
- **Apache Spark**: 3.2.1
- **MongoDB**: ReactiveMongo 1.1.0
- **Elasticsearch**: Elastic4S 7.2.0
- **Apache Ignite**: 2.4.0
- **Akka**: 2.5.32

## Common Development Commands

### Build and Compile
```bash
sbt compile           # Compile all modules
sbt ada-web/compile   # Compile specific module
sbt publishLocal      # Publish to local Maven repo
```

### Testing
```bash
sbt test                    # Run all tests
sbt core/test              # Run tests for specific module
sbt ada-web/test           # Run web app tests
sbt "testOnly *ClassName*" # Run specific test class
```

### Running the Application
```bash
sbt ada-web/run            # Run the web application (default port 9000)
sbt "ada-web/run 8080"     # Run on specific port
```

### Development Tools
```bash
sbt clean                  # Clean build artifacts
sbt ada-web/assets         # Compile web assets
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
- `project/build.properties`: SBT version (1.3.5)

## Development Guidelines

### Module Dependencies
The project follows a clear dependency hierarchy:
- `ada-web` depends on `ada-server` and `play`
- `ada-server` depends on storage modules (`elastic-json`, `mongo`, `ignite`) and `ml-spark`
- Storage modules depend on `core` and `json`
- All modules use centralized dependencies from `project/Dependencies.scala`

### Testing Strategy
- Tests are located in `src/test/scala/` within each module
- Uses ScalaTest 3.0.8 framework
- ScalaTestPlus Play 4.0.3 for web application testing
- Test resources in `src/test/resources/`

### Data Storage
- **MongoDB**: Primary document storage with ReactiveMongo
- **Elasticsearch**: Full-text search and document indexing
- **Apache Ignite**: Distributed caching and fast data access
- Unified JSON storage interface for backend flexibility

### Machine Learning
- **Apache Spark**: Large-scale ML algorithms and distributed processing
- **DL4J**: Deep learning and neural networks
- **Breeze**: Linear algebra and numerical computing
- Located in `ml-spark` and `ml-dl4j` modules

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
- **JDK**: Java 11 or higher
- **SBT**: 1.3.5 or higher
- **Node.js**: For frontend asset compilation
- **Git**: Version control

## Troubleshooting

### Common Issues
- **Out of Memory**: Increase JVM heap size with `-Xmx` settings
- **Port Conflicts**: Use `sbt "ada-web/run 8080"` for alternative port
- **Database Connection**: Check MongoDB/Elasticsearch service status
- **Compilation Errors**: Run `sbt clean` before rebuild

### Performance Optimization
- **Caching**: Apache Ignite for distributed caching
- **Database Indexing**: Ensure proper MongoDB/Elasticsearch indexes
- **Spark Configuration**: Adjust executor memory and cores for ML tasks
- **Play Configuration**: Tune connection pools and timeout settings