## 🚀 Milestone: Configurable & modular endpoints
 🌌 Road to Version 1.0

### 📝 Milestone Description

Currently endpoints are used providing HTTP typed interfaces via the `ServerVerticle` in NeonBee. Three types of interfaces are currently provided in NeonBee:

- A `raw` called endpoint exposing any kind of publicly named DataVerticle (DataVerticles starting with an upper case latin letter), in their native format. Currently essentially only JSON return types are supported.
- A `odata` endpoint, exposing fully-typed structured entity data of EntityVerticles via a OASIS OData V4 compliant interface using the Olingo library.
- And a (unused and mostly untested) `metrics` endpoint, exposing Vert.x metrics information.

This fix set of endpoints can currently be configured via the configuration file of the `ServerVerticle`. Basic properties are whether the endpoint is enabled or not and on which `basePath` the endpoint should be exposed. Some endpoints, such as the OData endpoint provide endpoint specific configurations, such as the `uriConversion` to use.

As a downside of the current implementation there is no way of exposing endpoints with different kinds of configuration, as there is a fix number of three endpoints. Additionally the endpoints do not provide customizability for settings like different authentication methods (`authenticationChain`) per endpoint, even though the server verticle could easily support that.

The idea of this roadmap item, is to make endpoints a generic and extensible concept in NeonBee. Instead of exposing the three endpoints fixed, the `ServerVerticle` configuration should provide the possibility to add and expose multiple endpoints, with multiple different configurations in an array definition. This will allow e.g. to expose multiple OData interfaces with different settings, like different authentication mechanisms used.

Certain server-level configurations, such as then authentication method, should also (additionally) be provided on the per-endpoint level. Configurations such as a positive or negative list of verticles / entities to expose via an endpoint should be added, such that one endpoint must no longer expose all DataVerticles or entity types.

The type of an endpoint should be determined by a generic `type` property. As for now only built-in types will be supported, however the concept could be later on extended to extract every endpoint type into an own sub-component / module of NeonBee, like `neonbee-endpoint-olingo-odatav4`, `neonbee-endpoint-cds-odatav4`, `neonbee-endpoint-openapi` or `neonbee-endpoint-admin` (see [admin console](E1_MS09_admin_console.md)). With that custom endpoints become a concept of external "web routes" to expose.

## Tasks / Features

- Make the endpoint configuration an array, instead of a object with only three types allowed
- Move server-level configurations to endpoint configurations, while keeping the "global" possibility on server level, like the `authenticationChain`
- Make existing endpoints more configurable, to e.g. be able to provide a positive / negative list which verticles to expose on the raw endpoint
- Introduce multiple endpoint types and prepare NeonBee with a generic concept for adding more endpoints in future