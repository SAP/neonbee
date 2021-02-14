## 🚀 Milestone: Other / smaller features & functions
 🌌 Road to Version 1.0

### 📝 Milestone Description

This milestone item collects other / smaller features for the current epic, which the NeonBee committers agreed on implementing, but did not fit into any existing milestone and / or putting them in an own milestone item is not sufficient.

## Tasks / Features

- Data models should be distributed via the event bus to all connected cluster nodes, so that models can act as a type-safe structured way of communication between the different nodes in the cluster. At the moment uploaded models are only managed on one node of the cluster. The `ModelManager` should be extended by a versioning and distribution capabilities for data models in the cluster.
- Add a prepare and post write phase to `DataVerticle` similar to the existing require data phase, for read requests. This can be later utilized for a build-in local / global / saga transaction handling.
- Add a build-in mechanism for short term result caching in the request chain e.g. via the context / in-memory or third party integrations, like an external Redis database.
- Optimize bootstrapping process, which currently sometimes even blocks during startup.
- Implement stop() for EntityVerticles.
- Impossible to send a manipulate data requests for an entity in case multiple EntityVerticles provide the same Entity.