# Edena Access ElasticSearch [![version](https://img.shields.io/badge/version-0.6.0-green.svg)](peterbanda.net) [![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](https://www.apache.org/licenses/LICENSE-2.0)

This is a convenient repo-like access layer for Elastic Search based on [Elastic4S](https://github.com/sksamuel/elastic4s) library.

#### Installation

All you need is **Scala 2.13**. To pull the library you have to add the following dependency to *build.sbt*

```
"org.edena" %% "edena-access-elastic" % "0.6.0"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>org.edena</groupId>
    <artifactId>edena-access-elastic_2.11</artifactId>
    <version>0.9.4</version>
</dependency>
```

respectively for Scala 2.12 as

```
<dependency>
    <groupId>org.edena</groupId>
    <artifactId>edena-access-elastic_2.12</artifactId>
    <version>0.9.4</version>
</dependency>
```

#### DB

Elasticsearch **8.x** is required (tested with 8.11+). The release for Debian-like Linux distributions can be installed as:

```sh
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-8.11.0-amd64.deb
sudo dpkg -i elasticsearch-8.11.0-amd64.deb
sudo systemctl enable elasticsearch.service
sudo service elasticsearch start
```
