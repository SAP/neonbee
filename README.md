# 🐝 NeonBee Core

[![Discord](https://img.shields.io/discord/786526477922336778.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/Fc3JWAHWFm)
[![REUSE status](https://api.reuse.software/badge/github.com/sap/neonbee)](https://api.reuse.software/info/github.com/sap/neonbee)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=neonbee_key&metric=coverage)](https://sonarcloud.io/dashboard?id=neonbee_key)

NeonBee is an open source reactive dataflow engine, a data stream processing framework using [Vert.x](https://github.com/eclipse/vert.x).

## Description

NeonBee abstracts most of Vert.x's low-level functionality by adding an application layer for modeling a dataflow, [data stream processing](https://en.wikipedia.org/wiki/Stream_processing) and for doing data consolidation, exposing everything via standardized APIs in form of interfaces called endpoints.

Additionally NeonBee takes care of all the execution and comes bundled with a full-blown application server with [all the core capabilities](https://vertx.io/docs/vertx-core/java/) of Vert.x at hand but without you having to care about things like a boot sequence, command line parsing, configuration, monitoring, clustering, deployment, scaling, logging / log handling and much more. Additionally, we put a rich convenience layer for data handling on top, simplifying technical data exchange.

To achieve this simplification, NeonBee reduces the scope of Vert.x by choosing and picking the most appropriate core and extensions components of Vert.x and providing them in a pre-configured / application server like fashion. For example, NeonBee comes with a default configuration of the [SLF4J](https://www.slf4j.org/) logging facade and [Logback](https://logback.qos.ch) logging backend, so you won't have to deal with choosing and configuring the logging environment in the first place. However, in case you decide to go with the logging setup NeonBee provides, you are still free to change.

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

NeonBee itself doesn't provide any examples yet, but thanks to [gedack](https://github.com/gedack) some examples can be found in this [repository](https://github.com/gedack/neonbee-examples).

The NeonBee data processing framework can be started like any other ordinary application server by using the start-up scripts provided in the root folder of NeonBee. By default, the boot sequence will check for verticles in the `/verticles` directory as well as from any remote source configured and deploy all these verticles automatically. This can be customized via command line and via a central configuration file in the `/config` directory. Also, the data model definition can be updated / loaded during runtime. To do so, valid CDS files must be put into the `/models` directory.

### Writing Your First Data Verticles

Writing a `DataVerticle` is a piece of cake. Imagine one of your verticles reads customer data from a database, while another verticle should do the processing of it. Let's start with the database verticle:

```java
public class DatabaseVerticle extends DataVerticle<JsonArray> {
    @Override
    public String getName() {
        return "Database";
    }

    @Override
    public Future<JsonArray> retrieveData(DataQuery query, DataContext context) {
        // return a future to a JsonArray here, e.g. by connecting to the database
        return Future.succeededFuture(new JsonArray()
            .add(new JsonObject().put("Id", 1).put("Name", "Customer A"))
            .add(new JsonObject().put("Id", 2).put("Name", "Customer B")));
    }
}
```

Note how the first verticle returns the name `Database`. By default, the name is set to the full qualified name of the class. We just return a name here to make the implementation of our processing verticle easier. We create a second data verticle called `ProcessingVerticle`. In order for the processing verticle to get data from the database verticle, a `DataRequest` can be returned from the processing verticles `requireData` method. NeonBee will try resolve all the dependencies of a data verticle, before invoking the verticles `retrieveData` method. This way it becomes very easy to request some data from another data verticle.

```java
public class ProcessingVerticle extends DataVerticle<JsonArray> {
    @Override
    public String getName() {
        return "CustomerData";
    }

    @Override
    public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        return Future.succeededFuture(List.of(new DataRequest("Database", query)));
    }

    @Override @SuppressWarnings("unchecked")
    public Future<JsonArray> retrieveData(DataQuery query, DataMap require, DataContext context) {
        AsyncResult<JsonArray> databaseResult = require.<JsonArray>findFirst("Database").orElse(null);
        if (databaseResult.failed()) {
            // retrieving data from the database failed
            return Future.failedFuture(databaseResult.cause());
        }

        // reverse the database result or do any other more reasonable processing ;-)
        List<Object> reversedDatabaseResult = new ArrayList<>();
        new ArrayDeque<Object>(databaseResult.result().getList())
            .descendingIterator()
            .forEachRemaining(reversedDatabaseResult::add);

        return Future.succeededFuture(new JsonArray(reversedDatabaseResult));
    }
}
```

And that's it. Deploy your verticles by simply putting a JAR file of them into NeonBees `/verticles` directory. We will soon release a sample repository on how to build a compliant JAR file. Compliant JAR files refer to so called "modules". A module JAR is a fat / ueber JAR, which comes with any dependencies of the verticle, excluding NeonBee / Vert.x dependencies which are provided by the framework.

Start-up the server, on its default port you can fetch the data of your verticle via the raw endpoint at http://localhost:8080/raw/CustomerData/. NeonBee can easily run this verticle in a highly scalable cluster setup out of the box.

### Advancing to Entity Verticles

Dealing with plain data verticles is great if you control who consumes the data and own all interfaces. The downside of data verticles is that they do not provide a structured and obliging interface, but if e.g. announcing to expose `JsonObject` the given JSON object could have any structure. In case you want to provide a public interface or simply need more control over the data returned, dealing with raw JSON, strings or even binary, gets a bit more troublesome.

NeonBee covers that with the concept of `EntityVerticle` an extension to the `DataVerticle` concept. Let's define a very simple data model using the CDS Definition Language first:

```
namespace MyModel;

entity Customers {
    key Id : Integer;
    Name : String;
}
```

Compile the CDS definition to EDMX.

Put the `.cds` and the resulting `.edmx` file in the `/models` directory of NeonBee. The OData endpoint now already knows about the Customers entity. The last thing to do is to actually define an `EntityVerticle` which returns the entity data on request:

```java
public class CustomerEntityVerticle extends EntityVerticle {
    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return Set.of(new FullQualifiedName("MyModel", "Customers"));
    }

    @Override
    public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        return Future.succeededFuture(List.of(new DataRequest("CustomerData", query)));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataMap require, DataContext context) {
        List<Entity> entities = new ArrayList<>();

        for (Object object : require.<JsonArray>resultFor("CustomerData")) {
            JsonObject jsonObject = (JsonObject) object;

            Entity entity = new Entity();
            entity.addProperty(new Property(null, "Id", ValueType.PRIMITIVE, jsonObject.getValue("Id")));
            entity.addProperty(new Property(null, "Name", ValueType.PRIMITIVE, jsonObject.getValue("Name")));
            entities.add(entity);
        }

        return Future.succeededFuture(new EntityWrapper(new FullQualifiedName("MyModel", "Customers"), entities));
    }
}
```

And that's it. You have created your first valid OData 4.0 endpoint in NeonBee. With the default configuration access the entity at http://localhost:8080/odata/Customers/. Similarly to data verticles the OData endpoint and your entity is now available and is highly scalable by NeonBee.

### For Your Convenience

NeonBee provides further simplifications when dealing with verticle development.

Especially in large-scale distributed systems, correlating log messages become crucial to reproduce what is actually going on. Conveniently, NeonBee offers a simple `LoggingFacade` which masks the logging interface with:

```java
// alternatively you can use the masqueradeLogger method,
// to use the facade on an existing SF4J logger
LoggingFacade logger = LoggingFacade.create();

logger.correlateWith(context).info("Hello NeonBee");
```

The logger gets correlated with a correlated ID passed through the routing context. In the default implementation of NeonBees logging facade, the correlation ID will be logged alongside the actual message as a so-called [marker](https://www.slf4j.org/faq.html#marker_interface) and can easily be used to trace a certain log message, even in distributed clusters. Note that the `correlateWith` method does not actually correlate the whole logging facade, but only the next message logged. This means you have to invoke the `correlateWith` method once again when the next message is logged.

Similar to Vert.x's shared instance, NeonBee provides its own shared instance holding some additional properties, such as the NeonBee options and configuration objects, as well as general purpose local and cluster-wide shared map for you to use. Each NeonBee instance has a one to one relation to a given Vert.x instance. To retrieve the NeonBee instance from anywhere, just use the static `NeonBee.neonbee` method of the NeonBee main class:

```java
NeonBee neonbee = NeonBee.neonbee(vertx);

// get access to the NeonBee CLI options
NeonBeeOptions options = neonbee.getOptions();

// general purpose shared local / async (cluster-wide) maps
LocalMap<String, Object> sharedLocalMap = neonbee.getLocalMap();
AsyncMap<String, Object> sharedAsyncMap = neonbee.getAsyncMap();
```

## Our Road Ahead

We have ambitious plans for NeonBee and would like to extend it to be able to provide a whole platform for dataflow / data stream processing. Generalizing endpoints, so further interfaces like OpenAPI can be provided or extending the data verticle concept by more optimized / non-deterministic resolution strategies are only two of them. Please have a look at our [roadmap](./docs/roadmap.md) for an idea on what we are planning to do next.

## Contributing

If you have suggestions how NeonBee could be improved, or want to report a bug, read up on our [guidelines for contributing](./CONTRIBUTING.md) to learn about our submission process, coding rules and more.

We'd love all and any contributions.
