# Health Checks

NeonBee provides a customizable health check registry, which can be extended
with application-specific health checks. The results of the health check
procedures are published via an health endpoint.

## Types of health checks

| name      | enabled    | description                                                           |
|-----------|------------|-----------------------------------------------------------------------|
| `memory`  | always     | checks if physical memory is below a threshold of 10%                 |
| `cluster` | clustered  | checks if cluster is in a safe state and lifecycle service is running |
| `node`    | clustered  | checks if local member is in a safe state                             |

### Custom Health Checks

Custom health checks can be added by implementing the
[HealthCheckRegistry](../src/main/java/io/neonbee/HealthCheckRegistry.java)
interface.

Example implementation of a database health-check:

```java
public class DatabaseHealthCheck implements HealthCheckRegistry {
    @Override
    public Future<Void> register(HealthChecks healthChecks, Vertx vertx) {
        return MongoService.getMongoService(vertx).compose(ms -> ms.getStatus()
            .map(ServiceStatus::isHealthy)
            .onComplete(asyncStatus -> healthChecks.register("database-health", 3000L, promise -> {
                if (asyncStatus.failed()) {
                    promise.fail(asyncStatus.cause());
                } else {
                    promise.complete(new Status().setOk(asyncStatus.result()));
                }
            })).mapEmpty());
    }
}
```

NeonBee tries to load all classes implementing this functional interface.
Adding a file `resources/META-INF/services/io.neonbee.HealthCheckRegistry` with
the full qualified path to the `DatabaseHealthCheck` will add the database
procedure to NeonBee's health check procedures.

## Configuration

In the `NeonBeeConfig`, the following properties can be configured:

- `healthCheckTimeout`: the number of seconds before a health procedure times
  out. Defaults to `1`.

## Exposing the health check

Health checks are enabled by default and the `HealthEndpoint` can be exposed
like any other endpoint of NeonBee in the ServerVerticle.yaml.

Example configuration in the `ServerVerticleConfig`:

```yaml
config:
  port: 8080
  endpoints:
    - type: io.neonbee.endpoint.health.HealthEndpoint
      enabled: true
      basePath: /health/
      authenticationChain: []
```
