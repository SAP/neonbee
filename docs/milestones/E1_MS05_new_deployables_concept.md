## 🚀 Milestone: New deployables concept
 🌌 Road to Version 1.0

### 📝 Milestone Description

Having a generic deployable type in NeonBee, was one of the first concepts introduced to Vulp.x. The initial idea was to either being able to put either deployable into individual folders in the working directory of NeonBee and NeonBee consuming those resources by deploying them to the cluster node, or by defining a bundle of these deployables in a Java artifact (JAR), defining the deployables in the MANIFEST.MF resource file of the JAR and having the whole package deployed by NeonBee.

This essentially started with verticles having the `@VulpxDeployable` and now `@NeonBeeDeployable` annotation only and a `deployables` manifest attribute. However soon the term of "module" was introduced, describing the JAR package format used to deploy to Vulp.x / NeonBee. The deployables concept afterwards stuck to the old terminology and was never revised to properly encapsulate all other types of deployables to NeonBee, like hooks, different types models, etc.

In the contributors community there are two different ideas on how to improve the current deployable concept. Either by sticking to the current deployable types and extending them with a `ModuleDeployable` or by introducing a new `ModuleManager` type class, which handles what has been deployed to NeonBee. Both concepts should be put into an RFC, evaluated for which one should be used going forward, decided upon by the contributors and implemented accordingly.

## Tasks / Features

- Write two RFCs for both ideas about a new deployable concept, weight them against each other and decide which one to implement
- Implement the new deployable concept for NeonBee