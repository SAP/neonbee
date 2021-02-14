## 🚀 Milestone: Abstract cloud layer for Cloud Foundry + Kubernetes
 🌌 Post 1.0 Era

### 📝 Milestone Description

Due to the original nature of NeonBee, as an internal project at SAP, it is by design very well suited to be hosted and operated on a cloud platform such as CloudFoundry (CF) and / or Kubernetes (K8S). For this purpose different cloud proprietary layers have been developed at SAP, to facilitate these cloud based environments. These layers for instance control setup of clusters, configuration for the server verticle and other associated services used in future, such as Redis for in-memory caching (see [other milestone goals of epic 1](E1_MS99_others.md)), or other persistent databases, such as MongoDB or HANA Cloud.

This milestone is about supporting the idea operating NeonBee on several cloud platforms, abstracting the still proprietary cloud layers into a general layer for NeonBee to be operated on different cloud platform environments. These layers facilitated by interfaces, will provide everything necessary for NeonBees functions to work on the given platform.

An abstraction has to be found, to support different environments, such as CloudFoundry (CF) and Kubernetes (K8S) have different ways of exposing services to the application, which the cloud layer needs to be able to abstract from. Additionally some features / services might not be available depending on the platform, thus the cloud layer should be able to account for which feature is available and can be configured on the given platform and whether it should be enabled, disabled or a native-implementation of NeonBee should be used if possible.

With the new abstraction layer for cloud, the existing proprietary layers should be migrated and thus made available open source as own components to use in NeonBee, like `neonbee-cloud-cf` and `neonbee-cloud-k8s`.

## Tasks / Features

- Create a cloud abstraction layer and generalize the proprietary cloud layers already available at SAP
- Port the existing proprietary layers for CloudFoundry and Kubernetes to the new generalized model
- Build a foundation to support different cloud layers in NeonBee and release the cloud layers open source