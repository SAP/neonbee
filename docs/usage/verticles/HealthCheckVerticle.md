# HealthCheckVerticle

The `HealthCheckVerticle` is a DataVerticle that is responsible for returning the current status of all registered
health checks. The results are retrieved from a central `HealthCheckRegistry`. It also registers itself in a shared map
if running in a cluster. This allows other verticles to access the `HealthCheckVerticle` through the shared map, and
makes it possible to retrieve health information on all nodes of the NeonBee cluster.