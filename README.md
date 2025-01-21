# 🐝 NeonBee Core

[![Discord](https://img.shields.io/discord/786526477922336778.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/Fc3JWAHWFm)
[![REUSE status](https://api.reuse.software/badge/github.com/sap/neonbee)](https://api.reuse.software/info/github.com/sap/neonbee)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=neonbee_key&metric=coverage)](https://sonarcloud.io/dashboard?id=neonbee_key)

NeonBee is an open source reactive dataflow engine, a data stream processing framework using [Vert.x](https://github.com/eclipse/vert.x).


* [Description](#description)
  * [NeonBee vs. Vert.x](#neonbee-vs-vertx)
  * [Core Components](#core-components)
  * [Dataflow Processing](#dataflow-processing)
    * [Data Verticles / Sources](#data-verticles--sources)
    * [Entity Verticles & Data Model](#entity-verticles--data-model)
    * [Data Endpoints](#data-endpoints)
* [Getting Started](#getting-started)
* [Our Road Ahead](#our-road-ahead)
* [Contributing](#contributing)

## Description

NeonBee abstracts most of Vert.x's low-level functionality by adding an application layer for modeling a dataflow, [data stream processing](https://en.wikipedia.org/wiki/Stream_processing) and for doing data consolidation, exposing everything via standardized RESTfull APIs in form of interfaces called endpoints.

Additionally NeonBee takes care of all the execution and comes bundled with a full-blown application server with [all the core capabilities](https://vertx.io/docs/vertx-core/java/) of Vert.x at hand but without you having to care about things like a boot sequence, command line parsing, configuration, monitoring, clustering, deployment, scaling, logging / log handling and much more. We put a rich convenience layer for data handling on top, simplifying technical data exchange.

To achieve this simplification, NeonBee reduces the scope of Vert.x by choosing and picking the most appropriate core and extensions components of Vert.x and providing them in a pre-configured / application server like fashion. For example, NeonBee comes with a default configuration of the [SLF4J](https://www.slf4j.org/) logging facade and [Logback](https://logback.qos.ch) logging backend, so you won't have to deal with choosing and configuring the logging environment in the first place. However, in case you decide to go with the logging setup NeonBee provides, you are still free to change.

### NeonBee vs. Vert.x

After reading the introduction, you might ask yourself *when do I need NeonBee* or *when to choose NeonBee over Vert.x*? Summarizing a few of our main advantages, that will let you help choose. If you say "no, I do that myself anyways", you might stay with Vert.x for now!

- **We *choose*, you *use***: With NeonBee we came to the conclusion that providing *all* capabilities of a framework is often not the best choice, especially when it comes to the later operation of the framework. Vert.x provides you with a lot of options to choose from: which cluster manager to take, which logging framework to choose, how many servers to start, what authentication plugins to use, etc. etc. This is great, if you know exactly what you need and everyone in your area that you are working in does. Problems start, when multiple projects should run on the same foundational basis, if it is required for your components to interact. Suddenly, choosing a different cluster manager, or event bus implementation, or for every team creating an own server in an own API format to expose their API soon gets troublesome! Thus, with NeonBee, we took a lot of those kinds of decisions for you and stuck to the industry / enterprise standard. Where there is debate, we made it configurable or provided hooks for you to integrate. However, you do no longer have to care, that your application is abel to run in a cluster, you need an API? You get an API, logging framework – pre-configured, metrics endpoint – check... NeonBee provides you with a perfect foundation to get started and for your team to build-up on, without having to deal with every bit and piece of the lower-level set-up of your project.

- **Configuration over code**: Vert.x has the concept of exposing everything via APIs to developers. It is a purely development-oriented framework. On the one hand side NeonBee is a development-oriented framework as well. Verticles still need implementation effort, however for especially the application server-side tasks, the idea is to configure a well-tested component, rather than writing a new one from scratch every time you need one. Need a RESTful JSON-based CRUD endpoint? Well, have fun writing one in Vert.x or simply add one line to a config file in NeonBee's `ServerVerticle` to get one. Want to configure your verticles with an own config file when they get deployed, well sure, go ahead and use Vert.x Config to make every verticle configurable, or use NeonBee and have every verticle that gets deployed, read a config from a `/config` directory automatically. We have built-in support for different endpoint types (like REST, OData and soon more), different authentication schemes, you can define an own error template, configure if and how you cluster your Vert.x instances, which Micrometer registries to use, etc. With this approach we want to set a standard, assuming that it is easier to implement it generically once in a framework, than it is to create it from scratch with every use case that you come across. And if there is still the need to, customize a given part of NeonBee via code, be our guest, you still have plenty of hooks and Vert.x default methods to override to do so!

- **Need an API? Get an API**: As briefly mentioned in the previous points already, Vert.x by default does not provide you with a standardized API endpoint. If you need one, build one, or use any of the Vert.x plugins to get one configured for you. How does it interact with other endpoints, is it properly integrated, e.g., into the event bus, so that consumption of thousands of requests from the endpoint remains scalable? Well, something that will depend heavily on your implementation of the server / endpoint. As a dataflow engine, we have been under the assumption that data processing means nothing, if you cannot expose your result. Thus, NeonBee comes with build-in endpoints and an extensible end-point concept. Meaning that you can get a REST and / or OData endpoint simply by configuring it. It runs integrated with the concept of `DataVerticle` and `EntityVerticle`, meaning that everything that you expose via a `DataVerticle` can also (without one line of additional code), be exposed via a REST endpoint if you choose to do so. Like back in the days, when you had been working on Servlets, you did not have to take care about that a server existed to expose the Servlets to, NeonBee provides you with a even extended concept of structured and unstructured endpoints, meaning that you can expose a full data model, of which the data is provided by an independently scalable set of verticles "under the hood". Again, one less thing you have to worry about!

- **Scalable by design**: Building a scalable application can be hard. Especially if there are too many choices and that one wrong decision or misuse of a certain functionality could have catastrophic effects in terms of performance and scalability. Vert.x is a great and very performant framework, two big parts of this is that it internally based on Netty ant their event-loop concept, as well as providing options to horizontally scale, such as the concept of verticles, the event-bus as well as running in a cluster of multiple Vert.x instances. However, connecting these dots can be hard... You missed to use the event-bus to communicate between your verticles? Well though-luck going to use any clustering. You want to use clustering but are not familiar with the concept of cluster managers, well that will be a chore for you to get into. You need to exchange data between your verticles efficiently, but you do not want to bother with all the internals of the event-bus, codecs, etc.? Yes, this is where NeonBee's build-in data processing / data flow concept comes into full effect! If you stick to the implementation of our `DataVerticle` and `EntityVerticle` interfaces, scalability will be simply a given, that you again, do not have to take care about! Verticles in NeonBee can communicate with each other via the event bus by design. At the same time, it is completely transparent to use as a user. You tell, which data you *require* and NeonBee does take care of the rest, requesting the data, in advance to your verticle getting the data it requested. And this being fully horizontally scalable, by using the build-in support for clustering and cluster management into NeonBee. Starting / operating your server as a single or a horizontally scalable clustered instance, is essentially just two more command line arguments when starting NeonBee. And this only works because we did not allow every verticle to "do whatever they want" there are a couple of rules to stick by, which, we know, are good boundary conditions for a development with Vert.x anyways.

- **Modularization is key**: Developing application in a monolithic design pattern, is a long accepted anti-pattern when it comes to scalable software development. In recent years micro-services had been en vogue, but they come with their own set of disadvantages. Generally micro-services mean, you need a full-stack experience in your developer-base. As the only "loose coupling rule" generally is that micro-services need to use the same protocol to communicate (for instance HTTP/S), there is a whole heck of things that every team needs to take care of. Well... now to put everything in one huge repository and to build one big Vert.x application out of it, might also not be the best thing when it comes to software design and maintainability. This is where NeonBee also shines: It comes with a build-in approach for componentization. Remember the `DataVerticles` and `EntityVerticles` that Team A build? Well, they should provide them to you a Fat/Ueber-Jar-like "Module Jar" format that NeonBee defines (essentially a Jar, that contains all dependencies of the verticles, but not shared dependencies, such as the dependency to NeonBee). NeonBee can then take this module and deploy it, into a completely encapsulated class-loader instance before deployment. This essentially allows you to build a scalable server, with as many verticles you would like, from as many teams as you would like. And like with micro-services, all verticles stay only loosely connected via the event-bus. Isn't that great!?

- **Trust us & don't worry, we got this!**: We have been working with Vert.x and together with the Vert.x community for many years now. We know the internals of Vert.x, Netty and learned a lot of how "the clock ticks". Meaning that sometimes we purposefully limited some options or provided *only* a customizability without the option to further influence a given component by using a development hook. In these cases, we did our homework, to ensure NeonBee stays scalable and performant at any point in time. This is also why for instance the default interface of a `DataVerticle` is very easy to use, even for somebody who had maybe not had any experience with Vert.x's futurized / promise-based interfaces so far. Give it a shot, we are sure you will like it! If you have anything to do better, tell us and we will be happy to help!

### Core Components

To facilitate the true nature of NeonBee, it features a certain set of core components, abstracting and thus simplifying the naturally broad spectrum of the underlying Vert.x framework components:

- **Server:** boot sequence (read config, deploy verticles & data models, start instance), master / slave handling, etc.
- **Command Line:** CLI argument parsing, using [Vert.x CLI API](https://vertx.io/docs/vertx-core/java/#_vert_x_command_line_interface_api)
- **Configuration:** YAML config, using [Vert.x Config](https://vertx.io/docs/vertx-config/java/)
- **Monitoring:** Using [Micrometer.io API and Prometheus](https://vertx.io/blog/eclipse-vert-x-metrics-now-with-micrometer-io/)
- **Clustering:** Clustered operation using any [Cluster Manager](https://vertx.io/docs/#clustering)
- **Deployment:** Automated deployment of verticles and data model elements
- **Supervisor & Scaling:** Automated supervision and scaling of verticles
- **Logging:** Using the [Vert.x Logging](https://vertx.io/docs/vertx-core/java/#_logging) and [SLF4J](https://www.slf4j.org/) facades and [Logback](https://logback.qos.ch/) as a back end
- **Authentication:** Configurable authentication chain using [Vert.x Auth](https://vertx.io/docs/vertx-auth-common/java/)

### Dataflow Processing

While you may just use the NeonBee core components to consume functionalities of Vert.x more easily, the main focus of NeonBee lies on data processing via its stream design. Thus, NeonBee adds a sophisticated high-performance data processing interface you can easily extend plug-and-play. The upcoming sections describe how to use NeonBees data processing capabilities hands-on. In case you would like to understand the concept of NeonBees dataflow processing more in detail, for instance on how different resolution strategies can be utilized, for a highly optimized traversal of the data tree, please have a look at [this document](./docs/dataflow_processing.md) explaining the theory behind NeonBees data processing.

#### Data Verticles / Sources

The main component for data processing is more or less a specialization of the verticle concept of Vert.x. NeonBee introduces a new `AbstractVerticle` implementation called `DataVerticle`. These types of verticles implement a very simple data processing interface and communicate between each other using the Vert.x [Event Bus](https://vertx.io/docs/vertx-core/java/#event_bus). Processing data using the `DataVerticle` becomes a piece of cake. Data retrieval was split in two phases or tasks:

1. **Require:** Each verticle first announces the data it requires from other even multiple `DataVerticle` for processing the request. NeonBee will, depending on the resolution strategy (see below), attempt to pre-fetch all required data, before invoking the next phase of data processing.
2. **Retrieve:** In the retrieval phase, the verticle either processes all the data it requested in the previous require processing phase, or it perform arbitrary actions, such as doing a database calls, or plain data processing, mapping, etc.

Conveniently, the method signatures of `DataVerticle` are named exactly like that. So, it is very easy to request / consolidate / process data from many different sources in a highly efficient manner.

During either phase, data can be requested from other verticles or arbitrary data sources, however, it is to note that those kinds of requests start spanning a new three, thus they can only again be optimized according to the chosen resolution strategy in their sub-tree. It is best to stick with one require / retrieval phase for one request / data stream, however it could become necessary mixing different strategies to achieve the optimal result, depending on the use case.

#### Entity Verticles & Data Model

Entity verticles are an even more specific abstraction of the data verticle concept. While data verticles can really deal with any kind of data, for data processing it is mostly more convenient to know how the data is structured. To define the data structure, NeonBee utilizes the [OASIS Open Data Protocol (OData) 4.0](http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html) standard, which is also internationally certified by ISO/IEC.

OData 4.0 defines the structure of data in so-called models. In NeonBee, models can be easily defined using the [Core Data and Services (CDS) Definition Language](https://cap.cloud.sap/docs/cds/cdl). CDS provide a human-readable syntax for defining models. These models can then be interpreted by NeonBee to build valid OData 4.0 entities.

For processing entities, NeonBee uses the [Apache Olingo™](https://olingo.apache.org/) library. Entity verticles are essentially data verticles dealing with Olingo entities. This provides NeonBee the possibility to expose these entity verticles in a standardized OData endpoint, as well as to perform many optimizations when data is processed.

#### Data Endpoints

Endpoints are standardized interfaces provided by NeonBee. Endpoints call entry verticles (see [dataflow processing](./docs/dataflow_processing.md)) to fetch / write back data. Depending on the type of endpoint, different APIs like REST, OData, etc. will be provided and a different set of verticles gets exposed. Given the default configuration, NeonBee will expose two simplified HTTP endpoints:

- A **/raw** HTTP REST endpoint that returns the data of any data verticle, preferably in a JSON  format and
- a standardized **/odata** HTTP endpoint to get a valid OData response from entity verticles.

## Getting Started

This [Kickstart Guide](https://github.com/SAP/neonbee-examples/blob/main/neonbee-kickstart-guide/README.md) is
recommended for a first start with NeonBee.

Further NeonBee examples can be found in this [repository](https://github.com/SAP/neonbee-examples).

## Our Road Ahead

We have ambitious plans for NeonBee and would like to extend it to be able to provide a whole platform for dataflow / data stream processing. Generalizing endpoints, so further interfaces like OpenAPI can be provided or extending the data verticle concept by more optimized / non-deterministic resolution strategies are only two of them. Please have a look at our [roadmap](./docs/roadmap.md) for an idea on what we are planning to do next.

## Contributing

If you have suggestions how NeonBee could be improved, or want to report a bug, read up on our [guidelines for contributing](./CONTRIBUTING.md) to learn about our submission process, coding rules and more.

We'd love all and any contributions.
