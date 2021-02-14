## 🚀 Milestone: Admin console / administrative user interface
 🌌 Road to Version 1.0

### 📝 Milestone Description

NeonBee currently comes without any user interface to interact with. Targeted to not only a development audience, but also to system engineers, looking for a stable operation of a fully asynchronous data processing engine / application server an admin user interface / cockpit is required and would help with the configuration, monitoring and operation of NeonBee. The admin UI could come as another endpoint type, to be configured alongside other modular endpoints (see [configurable & modular endpoints](E1_MS06_configurable_endpoints.md)) if required and should be a built-in endpoint providing a visual, browser-accessible, web application to administrate NeonBee. Exposing it via a endpoint has the added advantage, that a specific `authorizationChain` can be provided for restricting access to administrative users only.

This milestone item is about providing such an admin UI endpoint, expositing it to end users in a browser-based web application and making configuration, monitoring and operation-like tasks available to the user, accessing NeonBees exposed APIs. These, not exclusively should include:

- Downloading / displaying logs
- Configuring the log levels of loggers
- Cluster overview / management
- Deployment overview / bill of materials / deployment management, especially a list of all deployed Data and EntityVerticles, all deployed hooks and an overview of all data models available to the cluster
- A way to deploy / undeploy verticles at runtime, as well as enable / disable hooks and deploy / undeploy models to the cluster
- An UI for enabling tracing API if not covered by the log settings already
- A configuration UI, to maintain common configuration options, such as the `ServerVerticle` configuration at runtime (zero downtime required)
- A tool providing a log analysis (like communication errors and run times of certain verticles) for a given correlation id
- An overview about the schedule, next execution and the state of historical execution of JobVerticles

The whole UI should feel like a state of the art web UI and at best be responsive to multiple sizes of displays / mobile. Thus it should be considered using any open source web development framework, such as Angular, React, Vue.js or OpenUI5 to providing these capabilities.

## Tasks / Features

- Decide for a web development framework to use in order to be able to expose a modern admin UI interface in NeonBee
- Development of a endpoint type exposing an admin user interface
- Provide an user interfaces accessing NeonBees APIs to download / display logs, configure log, levels, etc. (see description above)