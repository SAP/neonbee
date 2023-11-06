package io.neonbee.internal.job;

import static io.neonbee.internal.deploy.DeployableVerticle.fromClass;
import static io.neonbee.internal.deploy.Deployables.fromDeployables;
import static io.neonbee.internal.scanner.DeployableScanner.scanForDeployableClasses;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeDeployable;
import io.neonbee.NeonBeeProfile;
import io.neonbee.data.DataContext;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.Registry;
import io.neonbee.internal.cluster.ClusterHelper;
import io.neonbee.internal.cluster.entity.ClusterEntityRegistry;
import io.neonbee.internal.deploy.Deployable;
import io.neonbee.internal.deploy.Deployables;
import io.neonbee.job.JobSchedule;
import io.neonbee.job.JobVerticle;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A job that redeploys all entity verticles that are not deployed in the cluster.
 */
@NeonBeeDeployable(namespace = NeonBeeDeployable.NEONBEE_NAMESPACE, autoDeploy = false)
public class RedeployEntitiesJob extends JobVerticle {

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(5L);

    /**
     * Create a new ReregisterEntitiesJob job verticle with the default configuration.
     */
    public RedeployEntitiesJob() {
        this(new JobSchedule(DEFAULT_INTERVAL));
    }

    /**
     * Create a new ReregisterEntitiesJob job verticle.
     *
     * @param schedule the schedule to use when starting this job verticle
     */
    public RedeployEntitiesJob(JobSchedule schedule) {
        this(schedule, false);
    }

    /**
     * Create a new ReregisterEntitiesJob job verticle. Optionally undeploy the verticle when the job execution ended
     * (hit the end instant or one time execution)
     *
     * @param schedule         the schedule to use when starting this job verticle
     * @param undeployWhenDone if true, undeploy the verticle when done
     */
    public RedeployEntitiesJob(JobSchedule schedule, boolean undeployWhenDone) {
        super(schedule, undeployWhenDone);
    }

    /**
     * Create a new ReregisterEntitiesJob job verticle from the given configuration.
     *
     * @param config the configuration
     * @return a new ReregisterEntitiesJob job verticle
     */
    public static RedeployEntitiesJob create(JsonObject config) {
        boolean undeployWhenDone = config.getBoolean("undeployWhenDone", false);
        Duration interval = Duration.parse(config.getString("interval", "PT5M"));
        JobSchedule jobSchedule = new JobSchedule(interval);
        return new RedeployEntitiesJob(jobSchedule, undeployWhenDone);
    }

    @Override
    public Future<?> execute(DataContext context) {
        LOGGER.correlateWith(context).info("Start scanning of missing entities in the cluster");
        long startTime = System.currentTimeMillis();

        // get the currently deployed entity verticles
        NeonBee neonBee = NeonBee.get(getVertx());
        Registry<String> entityRegistry = neonBee.getEntityRegistry();
        if (entityRegistry instanceof ClusterEntityRegistry) {
            LOGGER.correlateWith(context).debug("Getting registered entities from cluster");

            ClusterEntityRegistry clusterEntityRegistry = ((ClusterEntityRegistry) entityRegistry);
            Future<JsonArray> clusteringInformation = clusterEntityRegistry
                    .getClusteringInformation(ClusterHelper.getClusterNodeId(vertx))
                    .onSuccess(event -> LOGGER.correlateWith(context).debug("Got registered entities from cluster"))
                    .onFailure(error -> LOGGER.correlateWith(context)
                            .error("Failed getting registered entities from cluster", error));

            Future<Map<String, Class<? extends EntityVerticle>>> entitiesFromClassPath = classPathEntityVerticles(vertx)
                    .onSuccess(event -> LOGGER.correlateWith(context)
                            .info("Finished re-registering of entities. Took {} ms",
                                    System.currentTimeMillis() - startTime))
                    .onFailure(error -> LOGGER.correlateWith(context)
                            .error("Failed reregistering entities", error));

            return Future.all(clusteringInformation, entitiesFromClassPath)
                    .map(compositeFuture -> findMissingEntityVerticles(
                            context,
                            entitiesFromClassPath.result(),
                            clusteringInformation.result()))
                    .compose(difference -> deployMissingEntityVerticles(context, neonBee, difference))
                    .onFailure(error -> LOGGER.correlateWith(context)
                            .error("Failed getting registered entities from cluster", error));
        } else {
            // if it is not a clustered deployment we have nothing to do
            return Future.succeededFuture();
        }
    }

    /**
     * Find all entity verticles that are not deployed in the cluster.
     *
     * @param context               the data context
     * @param classPathEntitiesMap  the entity verticles from the class path
     * @param clusteringInformation the clustering information
     * @return a map of entity verticles that are not deployed in the cluster
     */
    private Map<String, Class<? extends EntityVerticle>> findMissingEntityVerticles(
            DataContext context,
            Map<String, Class<? extends EntityVerticle>> classPathEntitiesMap,
            JsonArray clusteringInformation) {
        Set<String> deployedEntitiesSet = qualifiedNamesSet(context, clusteringInformation);
        Map<String, Class<? extends EntityVerticle>> difference = new HashMap<>(classPathEntitiesMap);
        difference.keySet().removeAll(deployedEntitiesSet);
        return difference;
    }

    private Set<String> qualifiedNamesSet(DataContext context, JsonArray clusteringInformation) {
        Set<String> deployedEntitiesSet;
        if (clusteringInformation == null) {
            LOGGER.correlateWith(context).debug("No entities registered in cluster");
            deployedEntitiesSet = Set.of();
        } else {
            deployedEntitiesSet = clusteringInformation
                    .stream()
                    .map(jo -> (JsonObject) jo)
                    .map(jo -> jo.getString(ClusterEntityRegistry.QUALIFIED_NAME_KEY))
                    .collect(Collectors.toSet());
        }
        return deployedEntitiesSet;
    }

    private Future<Object> deployMissingEntityVerticles(DataContext context, NeonBee neonBee,
            Map<String, Class<? extends EntityVerticle>> difference) {
        if (difference.isEmpty()) {
            LOGGER.correlateWith(context).info(
                    "Skipping reconciliation as all EntityVerticles are already deployed on NeonBee node {}.",
                    neonBee.getNodeId());
            return Future.succeededFuture();
        } else {
            List<Future<? extends Deployable>> toDeploy = difference.entrySet()
                    .stream()
                    .peek(stringClassEntry -> LOGGER.correlateWith(context).info(
                            "Deploying missing EntityVerticles  \"{}\" on NeonBee node {}.",
                            stringClassEntry.getKey(),
                            neonBee.getNodeId()))
                    .map(stringClassEntry -> fromClass(vertx, stringClassEntry.getValue()))
                    .collect(Collectors.toList());

            return fromDeployables(toDeploy)
                    .compose(Deployables.allTo(neonBee))
                    .onSuccess(deployment -> LOGGER.correlateWith(context).info(
                            "Successfully deployed EntityVerticles \"{}\" on NeonBee node {}.",
                            deployment.getDeployable().getIdentifier(), neonBee.getNodeId()))
                    .mapEmpty();
        }
    }

    private Future<Map<String, Class<? extends EntityVerticle>>> classPathEntityVerticles(Vertx vertx) {
        return scanForDeployableClasses(vertx).map(verticles -> verticles.stream()
                .filter(EntityVerticle.class::isAssignableFrom)
                .filter(verticleClass -> filterByAutoDeployAndProfiles(verticleClass, activeProfiles()))
                .map(verticleClass -> (Class<? extends EntityVerticle>) verticleClass)
                .map(verticleClass -> {
                    NeonBeeDeployable annotation = verticleClass.getAnnotation(NeonBeeDeployable.class);
                    String namespace = annotation != null ? annotation.namespace() + "/" : "";

                    return new AbstractMap.SimpleEntry<String, Class<? extends EntityVerticle>>(
                            namespace + EntityVerticle.getName(verticleClass),
                            verticleClass);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existingValue, newValue) -> existingValue)));
    }

    private Set<NeonBeeProfile> activeProfiles() {
        return NeonBee.get(getVertx()).getOptions().getActiveProfiles();
    }

    @VisibleForTesting
    boolean filterByAutoDeployAndProfiles(Class<? extends Verticle> verticleClass,
            Collection<NeonBeeProfile> activeProfiles) {
        NeonBeeDeployable annotation = verticleClass.getAnnotation(NeonBeeDeployable.class);
        return annotation.autoDeploy() && annotation.profile().isActive(activeProfiles);
    }
}
