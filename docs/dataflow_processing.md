# Dataflow Processing

NeonBee is to be considered a reactive dataflow engine. [Dataflows](https://en.wikipedia.org/wiki/Dataflow) strongly relate to stream processing or a whole programming paradigm called [dataflow programming](https://en.wikipedia.org/wiki/Dataflow_programming) and the [dataflow architecture](https://en.wikipedia.org/wiki/Dataflow_architecture). Dataflows can be thought of as a directed graph modeling the data flowing between operations and, in contrast to a classical von Neumann / control flow architecture, solely the availability of data will determine which action is executed next, not some iterative set of instructions. The reactive and asynchronous nature of Vert.x support this behavior very well, however resulting in the order of the graph nodes to be processed to be nondeterministic, depending on the data resolution strategy used.

```
      (Shopping Cart)
       Entry Verticle
       /           \
      v             v
(User Info) <- (Sales Items) -> (Pricing API)
        \          /                  |
         v        v             [web request]
         (Database)
             |
         [mongo db]
```

Imagine in NeonBee every node or verticle (in the mathematical, not the Vert.x sense of the word) of the graph is represented by a `DataVerticle`. Every edge is determined by the verticles announced in the require phase, as described in the previous section. The contents of the node is determined in the second retrieval phase of data processing. The so called entry verticle, is a special name for the first `DataVerticle` invoked. It will finally, after all data was retrieved, determine the result of the operation. Imagine it as the root of the tree or graph. For one dataflow there is always exactly one entry verticle, depending whether or not an entry verticle should be exposed (e.g. via an endpoint, see later chapters), different notations can be used.

## Data Resolution Strategies

For retrieving data from the verticles, multiple so called resolution strategies can be used, however in one data stream, only one strategy is allowed. Mixing strategies is not possible, however in each phase of data processing, new requests can be sent. This might decrease the performance of the whole stream though. Imagine resolution strategies determining the order or process in which the graph of verticles is traversed. Each strategy comes with different traits and benefits, such as whether the strategy guarantees a deterministic order when certain verticles are executed, or wether different stages of verticles are called sequentially or not.

### Recursive Strategy

At the moment NeonBee offers only the recursive data resolution strategy. With the recursive strategy imagine each node and data requests to be processed in single stages:

| Entry Stage | Stage 1 | Stage 2 | Stage 3 |
| :---: | :---: | :---: | :---: |
| Shopping Cart | User Info | User Info | Database |
| | Sales Items | Database | |
| | | Pricing API | |

Note how the "User Info" verticle, as well as the "Database" verticle are both part of the first and second or the second and third stage. The recursive strategy will start to ask the first verticle what data it requires ("User Info" and "Sales Items"), two requests for data are then sent to those verticles, them announcing data is needed from "User Info", "Database" and the "Pricing API". Note how, due to the recursive nature of this strategy, the "User Info" verticle is already called twice. As "User Info" again calls the "Database" verticle, a third stage is required. "Database" and "Pricing API" can be considered leaves of the directed graph and thus exit verticles, verticles requiring data from no other verticles. The recursive strategy can be considered deterministic in its stage, as all requests will be processed sequentially. The downside of this approach is, that even if multiple verticles require the same data, no deduplication is performed and multiple data requests may be sent twice to a verticle. Another drawback of this approach is that multiple require / retrieve cycles are needed, to process the dataflow. Thus even when only doing one request, data processing may slow down, as downstream verticles, have not received their data request yet.

| | |
| --- | --- |
| **Deterministic** | ✔️ in each stage |
| **Deduplication** | ❌ calls to single verticles could happen multiple times, even in one stage |
| **Sequential Calls** | one require / retrieve phase per stage, processing at least takes 2 × n-steps |

### Prefetch Strategy

***Note:*** This strategy is not available in NeonBee yet, but we have it on our [roadmap](./roadmap.md).

Instead of recursively requesting data and spanning the tree in a call stack, NeonBee first collects all required data from all verticles in the dataflow graph. In the example above the result would be a set of requests to the data:

`Required( Shopping Cart ) = { User Info, Sales Items, Pricing API, Database }`

Note how not a list, but really a set of required data is collected. Any duplicates are removed. In this simplified example we neglect that multiple different requests could be sent to for instance the database, NeonBee for sure takes care of this, however to explain the concept we assume every request to a verticle to be an equal request.

NeonBee now sends requests to the verticles which all required data is already available. Thus first all requests are sent to verticles requiring no data, so in the graph, those verticles which have no outgoing arrows, like "Database" and "Pricing API". After the data for "Database" becomes available, "User Info" can be resolved and after that all other verticles will follow.

This strategy allows for an much more optimal data resolution. However the deduplication of requests and calling the verticle solely by if the data is available or not, cause this strategy to follow a non-deterministic order of verticle executions.

| | |
| --- | --- |
| **Deterministic** | ❌ verticles will be called as soon as all its required data is ready |
| **Deduplication** | ✔️ if the same request is sent to the same verticle multiple times, the verticle will only be called once and the result will be provided to both requestors |
| **Sequential Calls** | only one execution of one require and retrieve phase in total |