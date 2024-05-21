# Ada Server [![version](https://img.shields.io/badge/version-0.9.2-green.svg)](https://peterbanda.net) [![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](https://www.apache.org/licenses/LICENSE-2.0)

<img src="/ada-web/public/images/logos/ada_logo_v1.png" width="450px">

The project serves as a server part of Ada Discovery Analytics platform providing:

* Domain classes with JSON formatters.
* Persistence layer with convenient repo abstractions for Mongo, Elastic Search, and Apache Ignite. 
* WS clients to call REDCap, Synapse, and eGaIT REST services.
* Data set importers and transformations.
* Stats calculators with Akka streaming support.
* Machine learning service providing various classification, regression, and clustering routines backed by Apache Spark.

#### Installation

If you want to use *Ada Server* in your own project all you need is **Scala 2.12**. To pull the library you have to add the following dependency to *build.sbt*

```
"org.edena" %% "ada-server" % "0.9.2"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>org.edena</groupId>
    <artifactId>ada-server_2.12</artifactId>
    <version>0.9.2</version>
</dependency>
```

#### License

The project and all its source code is distributed under the terms of the <a href="https://www.apache.org/licenses/LICENSE-2.0.txt">Apache 2.0 license</a>.
