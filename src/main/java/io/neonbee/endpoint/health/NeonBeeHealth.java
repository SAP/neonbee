package io.neonbee.endpoint.health;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.ServiceLoader;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.HealthCheckRegistry;
import io.neonbee.endpoint.health.checks.ClusterHealthChecks;
import io.neonbee.endpoint.health.checks.DefaultHealthChecks;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class NeonBeeHealth {

    /**
     * The Vert.x {@link HealthChecks}.
     */
    public HealthChecks healthChecks;

    /**
     * The cluster manager.
     */
    public HazelcastClusterManager clusterManager;

    /**
     * The health-check procedure timeout.
     */
    public long timeout;

    /**
     * Whether clustered health-check procedures should be registered.
     */
    @VisibleForTesting
    boolean clustered;

    private final Vertx vertx;

    /**
     * Constructs an instance of {@link NeonBeeHealth}.
     *
     * @param vertx The current Vert.x instance
     */
    public NeonBeeHealth(Vertx vertx) {
        this.vertx = vertx;
        this.healthChecks = HealthChecks.create(vertx);
    }

    /**
     * Starts the health-checking of NeonBee by registering all default health-checks and cluster health-checks if and
     * only if NeonBee is started in the clustered mode. It also registers all health-checks of classes loaded by the
     * {@link ServiceLoader} which implement the {@link HealthCheckRegistry} interface.
     *
     * @return a succeeded Future if registering all health-checks succeeds, a failed Future otherwise.
     */
    public Future<Void> start() {
        List<Future<Void>> checkList = ServiceLoader.load(HealthCheckRegistry.class).stream()
                .map(c -> c.get().register(healthChecks, vertx)).collect(toList());

        checkList.add(DefaultHealthChecks.register(this));
        if (clustered) {
            checkList.add(ClusterHealthChecks.register(this));
        }

        return CompositeFuture.all((List<Future>) (Object) checkList).mapEmpty();
    }

    /**
     * Sets the timeout of the health checking in seconds.
     *
     * @param timeout the timeout (in ms)
     * @return the {@link DefaultHealthChecks}
     */
    @Fluent
    @SuppressWarnings("checkstyle:MagicNumber")
    public NeonBeeHealth setTimeout(int timeout) {
        this.timeout = timeout * 1000L;
        return this;
    }

    /**
     * Enables clustered health-checks.
     *
     * @param clusterManager the {@link HazelcastClusterManager}
     * @return the {@linkplain NeonBeeHealth} for fluent usage
     */
    @Fluent
    public NeonBeeHealth enableClusteredChecks(HazelcastClusterManager clusterManager) {
        this.clusterManager = requireNonNull(clusterManager);
        this.clustered = true;
        return this;
    }
}
