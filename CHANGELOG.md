# Changelog

## 1.1.1 (2026-09-09)

- **Apache Spark 3.5.4 → 3.5.6**: patch release with CVE fixes; Spark 4.0 deferred because of its breaking changes.
- **ScalaTest 3.0.8 → 3.2.19** and **ScalaTestPlus Play 4.0.3 → 6.0.2** (Play 2.9.x line): all test suites migrated to the 3.1+ style-trait packages (`org.scalatest.flatspec.*`, `org.scalatest.funsuite.*`, `org.scalatest.matchers.should.Matchers`); the `kafka` module now uses the shared ScalaTest version instead of its own pin.
- **Reflections 0.9.10 → 0.10.2** (`ada-server`): the class finder builds its scanner from an explicit classpath configuration, since the no-arg constructor became protected; the old findbugs exclusion is no longer needed.

## 1.1.0 (2026-09-09)

- **Platform upgrade**: Play 2.9.6, Akka 2.6.21, Play JSON 2.10, Jackson 2.14, Apache Spark 3.5.4, Apache Ignite 2.14, sbt 1.9.6, PAC4J 12 / OIDC 6.2, Deadbolt 2.9, ReactiveMongo for Play 2.9; dependency pins for known CVEs (Netty, commons-lang3, commons-io, jQuery UI 1.14, D3 v7).
- **Elasticsearch 8.x**: Elastic4s 8.19; kNN (vector) and nested-document search, fuzzy search settings, object-vs-nested mappings, basic auth, and a dynamic read-only store built from an index's live mapping.
- **New `scripting` module**: GraalVM polyglot JavaScript and Python execution with thread-safe "default" and "admin" pools, per-run variable isolation, and lazy context recycling.
- **New `kafka` module**: producer and Akka Streams consumers with Schema Registry serialization and dead-letter-queue support.
- **Secrets**: symmetric (AES-GCM) encryption of `enc:v1:`-prefixed config values and environment variables, master key and optional pepper supplied from files or systemd/Docker secret locations, plus an env-file encoder (CLI, Ada runnable, and web UI).
- **Charts**: four pluggable widget engines — Plotly (default), ApexCharts, ECharts, and Highcharts. Highcharts is proprietary and is no longer bundled; it loads from Highsoft's CDN (or a deployer-supplied webjar) and the data set settings show a licensing notice when it is selected.
- **Exports**: exports stream with a per-export batch size chosen as Low / Medium / High in the export dialogs (sizes configurable under `elastic.scroll.batch.levels`), instead of the global scroll size; shipped global default lowered from 10000 to 1000. JSONL export and JSON/JSONL item import added across CRUD views, with an in-browser JSON item editor.
- **Statistics and UI**: binned box and aggregation widgets, Spearman correlation, date-binned distributions, run-script screens, and refreshed form styling.
- **Build hygiene**: the Barnes-Hut t-SNE implementation is vendored (BSD-3) so the build no longer depends on jitpack; reflection-based JSON format/schema helpers; expanded Elastic mapping and nested-search test suites.

## 1.0.0 (2025-05-20)

- Scala 2.13 migration and first Edena Suite release.
