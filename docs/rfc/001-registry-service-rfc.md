# NeonBee Event-Driven Service Registry RFC 001

## Contents
- [Abstract](#abstract)
- [Motivation](#motivation)
- [Technical Details](#technical-details)
- [Performance Considerations](#performance-considerations)
- [Impact on Existing Functionalities](#impact-on-existing-functionalities)
- [Open Questions](#open-questions)
- [Alternatives](#alternatives)

## Abstract

This RFC proposes a more reliable method of EntityVerticle service discovery and registration within the NeonBee ecosystem. The current system, has proven to be error-prone and unresponsive to changes in entity service availability or configuration. This proposal presents an Event-Driven Service Registry Microservice that centralizes service registration and discovery. It provides real-time updates through event publications and subscriptions on the vert.x even-bus.

## Motivation

This RFC addresses recurring issues within our microservices architecture, where EntityVerticle's fail to register successfully or continue to be listed as available long after becoming unresponsive. These problems have led to service outages, degraded performance, and an overall lack of system resilience. The key motivations for this RFC are:

- **Dynamic Service Scalability**: As services scale up or down, the registry must immediately reflect these changes to avoid routing requests to non-existent EntitiyVertilce instances.
- **Service Resilience**: The system should gracefully handle service failures, ensuring that dependent services are not impacted by stale or incorrect registry entries.
- **Simplified Configuration Management**: By centralizing EntityVerticle service discovery, individual services no longer need to manage their discovery mechanisms, reducing complexity.
- **Decoupling Implementation**: By moving the implementation to its own service, the code is better

## Technical Details

I propose the possibility to start the EntityVerticle Registry as a separate verticle. This verticle can be configured to replace the EntityVerticle Registry if needed and handles all requirements, registration, deregistration, ...
The proposed Event-Driven EntityVerticle Registry Microservice will implement the following key functionalities:

- **EntityVerticle Registration**: Upon startup, services will send a registration event containing their unique identifier and EventBus address to the registry microservice.
- **EntityVerticle Deregistration**: Services will send a deregistration event upon graceful shutdown. Additionally, the registry will deregister services failing periodic health checks.
- **EntityVerticle Publication**: The registry microservice will publish events to a dedicated EventBus topic whenever a service is registered, deregistered, or changes its status. This allows all consuming services to update their local caches in real-time.
- **Health Checks**: Periodic health checks will be implemented to verify the availability of registered services, ensuring the registry remains accurate and up-to-date.

Services subscribe to registry update events to receive the latest information without the need for active polling.

## Performance Considerations

The performance should not be affected. The basic functionality already works like it is explained here.
Key performance considerations include:

- **Event Bus Load**: The load is expected to be low. Usually services run for several hours or longer. To pay attention to the volume of events an alerting should be implemented to ensure that the event bus is not overwhelmed by registry update traffic.
- **Registry Performance**: The registry microservice must be optimized for high throughput and low latency to handle the dynamic nature of microservices registration and deregistration.

## Impact on Existing Functionalities

Implementing this RFC will require modifications to how services register themselves and discover other services within the NeonBee ecosystem. Existing services will need to adopt the new event-driven registration mechanism. While this change is fundamental, it is expected to enhance system stability and scalability without negatively impacting existing business logic or service interactions.

## Open Questions

- **Migration Path**: What is the most efficient way to transition existing services to this new registry mechanism without significant downtime?
- **Event Schema**: What should the event schema look like for service registration/deregistration and health check events?
- **Registry Backup**: How do we ensure the resilience of the registry service itself, preventing it from becoming a single point of failure?

## Alternatives

### Configuration Management Microservice
A Configuration Management Microservice can centralize and streamline the handling of system-wide configurations and settings. This microservice would:

- **Centralize Configuration Storage**: Serve as the authoritative source for all configuration settings, ensuring consistency across the system.
- **Support Dynamic Configuration**: Allow services to update their configurations on the fly without the need for restarts. This is particularly useful for adjusting parameters like logging levels, feature flags, or service thresholds.
- **Provide Versioning and Rollback**: Maintain versions of configurations, allowing for quick rollbacks in case of issues with new configuration deployments.
- **Event-Driven Updates**: Similar to the Service Registry Microservice, this microservice can publish configuration change events to which other services can subscribe. This ensures that all services are updated in real-time when configurations change.

Implementing a dedicated Configuration Management Microservice enhances the system's flexibility and responsiveness to changes. It allows for centralized management of configurations, reducing the complexity and potential for errors associated with managing configurations across multiple services.

Combining these approaches, you leverage the benefits of an event-driven system to ensure real-time updates and communication between services, significantly reducing the complexity and error-proneness of the current system. This setup also makes the system more scalable and easier to manage, as it abstracts the service discovery and configuration management into dedicated, specialized microservices.