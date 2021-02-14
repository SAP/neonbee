## 🚀 Milestone: Response context / hints & data types
 🌌 Road to Version 1.0

### 📝 Milestone Description

NeonBee currently exposes Data/EntityVerticles to interfaces via two main endpoints:

1. A (currently) "raw" called endpoint, which exposes DataVerticles with a built-in logic of the endpoint (if JsonObject/Array is returned result in application/json and if other data types are returned try to serialize them to text or binary)
2. A OData endpoint exposing EntityVerticles

NeonBee currently has no concept of the verticles influencing the endpoints. Also all HTTP / format / encoding logic is encapsulated in the endpoints and transparent to the verticles.

This milestone is about providing especially entry verticles (the first verticle called by an endpoint) the possibility to provide "hints" to the endpoint on how to behave. It was decided that the DataContext can be used to achieve that. However the content of a DataContext can currently be modified by all entities in the request queue, which could result in issues with response handling by the endpoints, if multiple verticles modify the context. For this reason a new DataContext object for every response should be introduced, which is not automatically propagated in the verticle chain. This special context part can then be used to influence the endpoints, by hints given by the entry verticle.

Additionally this item should be used to generally improve response handling of DataContext because at the moment the DataContexts returned by multiple verticles are merged together without any deterministic merging strategy.

The endpoints can provide different kinds of hints, depending on what they support. One hint to introduce to the raw endpoint, is how to handle the response data. For example which content type and or status/codes / exceptions to return. By default any exception results in a 500 internal server error. Hints could be used, to even though the result of the promise chain is a success, the HTTP request is returned with an arbitrary HTTP error.

## Tasks / Features

- Response-Only DataContext part / Context Response
- Hints to Endpoints
- Content-Type Hint in Raw Endpoint
- Error Response in Raw Endpoint
- Response Context parsing / merging of different DataContext
- Adding support for custom responses