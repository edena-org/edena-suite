# Ada Web [![version](https://img.shields.io/badge/version-0.9.2-green.svg)](https://peterbanda.net) [![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](https://www.apache.org/licenses/LICENSE-2.0)

<img src="/ada-web/public/images/logos/ada_logo_v1.png" width="450px">

This is a web part of Ada Discovery Analytics serving as a visual manifestation of [Ada server](https://github.com/edena-org/edena-suite/ada-server).  In a nutshell, _Ada web_ consists of controllers with actions whose results are rendered by views as actual presentation endpoints produced by an HTML templating engine. It is implemented by using [Play](https://www.playframework.com) Framework, a popular web framework for Scala built on asynchronous non-blocking processing.

Ada's main features include a visually pleasing and intuitive web UI for an interactive data set exploration and filtering, and configurable views with widgets presenting various statistical results, such as, distributions, scatters, correlations, independence tests, and box plots.  Ada facilitates robust access control through LDAP authentication and an in-house user management with fine-grained permissions backed by [Deadbolt](http://deadbolt.ws) library.

#### Installation

Install all the components including Mongo and Elastic Search _manually_, which gives a full control and all configurability options at the expense of moderate installation and maintenance effort. The complete guides are availble for  [Linux](Installation_Linux.md) and [MacOS](Installation_MacOS.md).
Note that if instead of installing a stand alone Ada app you want to use the _Ada web_ libraries in your project you can do so by adding the following dependencies in *build.sbt* (be sure the Scala compilation version is **2.12**)

```
"org.edena" %% "ada-web" % "0.9.2",
"org.edena" %% "ada-web" % "0.9.2" classifier "assets"
```

Alternatively if you use maven  your *pom.xml* has to contain

```
<dependency>
    <groupId>org.edena</groupId>
    <artifactId>ada-web_2.12</artifactId>
    <version>0.9.2</version>
</dependency>
<dependency>
    <groupId>org.edena</groupId>
    <artifactId>ada-web_2.12</artifactId>
    <version>0.9.2</version>
    <classifier>assets</classifier>
</dependency>
```

#### License

The project and all source its code is distributed under the terms of the <a href="https://www.apache.org/licenses/LICENSE-2.0.txt">Apache 2.0 license</a>.
