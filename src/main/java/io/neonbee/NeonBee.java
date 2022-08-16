package io.neonbee;

import static io.neonbee.internal.deploy.DeployableModule.fromJar;
import static io.neonbee.internal.deploy.DeployableVerticle.fromClass;
import static io.neonbee.internal.deploy.DeployableVerticle.fromVerticle;
import static io.neonbee.internal.deploy.Deployables.allTo;
import static io.neonbee.internal.deploy.Deployables.anyTo;
import static io.neonbee.internal.deploy.Deployables.fromDeployables;
import static io.neonbee.internal.helper.AsyncHelper.allComposite;
import static io.neonbee.internal.helper.AsyncHelper.joinComposite;
import static io.neonbee.internal.helper.HostHelper.getHostIp;
import static io.neonbee.internal.scanner.DeployableScanner.scanForDeployableClasses;
import static io.vertx.core.CompositeFuture.all;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.neonbee.config.HealthConfig;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.data.DataException;
import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityModelManager;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.health.EventLoopHealthCheck;
import io.neonbee.health.HazelcastClusterHealthCheck;
import io.neonbee.health.HealthCheckProvider;
import io.neonbee.health.HealthCheckRegistry;
import io.neonbee.health.MemoryHealthCheck;
import io.neonbee.health.internal.HealthCheck;
import io.neonbee.hook.HookRegistry;
import io.neonbee.hook.HookType;
import io.neonbee.hook.internal.DefaultHookRegistry;
import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.internal.buffer.ImmutableBuffer;
import io.neonbee.internal.codec.DataExceptionMessageCodec;
import io.neonbee.internal.codec.DataQueryMessageCodec;
import io.neonbee.internal.codec.EntityWrapperMessageCodec;
import io.neonbee.internal.codec.ImmutableBufferMessageCodec;
import io.neonbee.internal.codec.ImmutableJsonArrayMessageCodec;
import io.neonbee.internal.codec.ImmutableJsonObjectMessageCodec;
import io.neonbee.internal.deploy.Deployable;
import io.neonbee.internal.deploy.Deployables;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.internal.helper.FileSystemHelper;
import io.neonbee.internal.json.ImmutableJsonArray;
import io.neonbee.internal.json.ImmutableJsonObject;
import io.neonbee.internal.scanner.HookScanner;
import io.neonbee.internal.tracking.MessageDirection;
import io.neonbee.internal.tracking.TrackingDataHandlingStrategy;
import io.neonbee.internal.tracking.TrackingDataLoggingStrategy;
import io.neonbee.internal.tracking.TrackingInterceptor;
import io.neonbee.internal.verticle.ConsolidationVerticle;
import io.neonbee.internal.verticle.DeployerVerticle;
import io.neonbee.internal.verticle.HealthCheckVerticle;
import io.neonbee.internal.verticle.LoggerManagerVerticle;
import io.neonbee.internal.verticle.MetricsVerticle;
import io.neonbee.internal.verticle.ModelRefreshVerticle;
import io.neonbee.internal.verticle.ServerVerticle;
import io.vertx.core.Closeable;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

@SuppressWarnings({ "PMD.CouplingBetweenObjects", "PMD.GodClass" })
public class NeonBee {
    /* // @formatter:off
     *
     *                             ,"`  ´\,                                     -" ,+´\
     *                            ` \     ",                                  /   '    \
     *                           /        ´\                                /   ,       |
     *                           ,  ´     ´ '                             ,"   ,        '
     *                           |   ´    ´  \                           /     `     , /
     *                           ´    ´  /",  |                         /`    `    ,'  /
     *                           |     \.    \|                        \`´,  ` ,,,'   /
     *                            \ ´´"| "   ,/                     ,\`   \/" ,"    ,
     *                             \,   |`    |                     ,``   /- /`  ` ,-`
     *                    ,-,      ´´   | .-- |                    ,`` ,'  /`   '   /
     *                  /,-+ ,      \´,+"-"" |´                   ,`"`"  -    /    /
     *                /,"   \´      /|"      |                   , "  ,"",  /    /`
     *               \`       \    ,,//////// , "               ,`/  '   ,,`   /`
     *                       ' \,///////////\ /\                `'\/  ,'     /
     *                        \ /\/////"""``\ /,       .,-`    `/   ,"    ,"
     *                    ,+|"," ´"""       ´,' ", ,+/" /"|   `/   "    ,'
     *                 ,//",+/                  ,/+" -"   \" "/       ,"
     *              ,//`,-` /     ,,,,,,,,....//` /"        ',    / /`
     *            /---"""""""""""```          ,+" /        /,     /
     *            \           -,             \    ,,      / `   /
     *             '         /  ,           /"   ,|    "-/ `  /
     *              ",      ,    \        ,|"    ||     , " /
     *                ´"--/"/      ´"--++"`     ||/     `' /  \
     *                    /                   ,|||    ´.""`   ´"\
     *                    \+                ,||||`          \    "
     *                 ," /\+"          ,+/||||"             \    ´,
     *               /`,'      ´`""|""\|||+""`                /\    ,
     *                ,            |                        ,||||\,  \
     *         ,,,  / `            ´                      ,/|||||| \  '
     *        "-,   `,´" -,         \                  ,/||||||||"  \  ´
     *        /` -.,"" ----\         \,            ,,/||||||||/"     \  ´
     *                 ´´`|        /  ||/\++++///||||||||||/"         \  ´
     *      ,"           ,            ` \|||||||||||||/""""``      `" -/  \
     *      `/   NEON     `        |  /    "|||||/""  ,.- ""``   ´"""-.,   ,
     *       \    BEE   /',       `         ´/" ,-"`            ´        ´
     *      ',\        /,  ".    ,  |      ',-"´\
     *        ´"-     ,  ´\   "-,/  /    ,,`
     *      |         `     ".    \ `
     *        " -..,,'         ´"--/
     *
     */ // @formatter:on

    private static final String CORRELATION_ID = "Initializing-NeonBee";

    private static final Logger LOGGER = LoggerFactory.getLogger(NeonBee.class);

    private static final Map<Vertx, NeonBee> NEONBEE_INSTANCES = new HashMap<>();

    private static final String SHARED_MAP_NAME = "#sharedMap";

    private static final int NUMBER_DEFAULT_INSTANCES = 4;

    @VisibleForTesting
    final NeonBeeConfig config;

    private final String nodeId = UUID.randomUUID().toString();

    private final Vertx vertx;

    private final NeonBeeOptions options;

    private final HookRegistry hookRegistry;

    private final HealthCheckRegistry healthRegistry;

    private LocalMap<String, Object> sharedLocalMap;

    private AsyncMap<String, Object> sharedAsyncMap;

    private final Set<String> localConsumers = new ConcurrentHashSet<>();

    private final EntityModelManager modelManager;

    private final CompositeMeterRegistry compositeMeterRegistry;

    private final HazelcastClusterManager clusterManager;

    /**
     * Convenience method for returning the current NeonBee instance.
     * <p>
     * Important: Will only return a value in case a Vert.x context is available, otherwise returns null. Attention:
     * This method is NOT signature compliant to {@link Vertx#vertx()}! It will NOT create a new NeonBee instance,
     * please use {@link NeonBee#create(NeonBeeOptions)} or
     * {@link NeonBee#create(Function, NeonBeeOptions, NeonBeeConfig)} instead.
     *
     * @return A NeonBee instance or null
     */
    public static NeonBee get() {
        Context context = Vertx.currentContext();
        return context != null ? get(context.owner()) : null;
    }

    /**
     * Get the NeonBee instance for any given Vert.x instance.
     *
     * @param vertx The Vert.x instance to get the NeonBee instance from
     * @return A NeonBee instance or null
     */
    public static NeonBee get(Vertx vertx) {
        return NEONBEE_INSTANCES.get(vertx);
    }

    /**
     * Create a new NeonBee instance, with default options. Similar to the static {@link Vertx#vertx()} method.
     *
     * @return the future to a new NeonBee instance initialized with default options and a new Vert.x instance
     */
    public static Future<NeonBee> create() {
        return create(new NeonBeeOptions.Mutable());
    }

    /**
     * Create a new NeonBee instance, with the given options. Similar to the static Vert.x method.
     * <p>
     * Note: This method is NOT a static method like {@link Vertx#vertx(VertxOptions)}, as no factory method is needed.
     *
     * @param options the NeonBee command line options
     * @return the future to a new NeonBee instance initialized with default options and a new Vert.x instance
     */
    public static Future<NeonBee> create(NeonBeeOptions options) {
        return create((OwnVertxFactory) (vertxOptions) -> newVertx(vertxOptions, options), options, null);
    }

    /**
     * Create a new NeonBee instance, with the given options. Similar to the static Vert.x method.
     * <p>
     * Note: This method is NOT a static method like {@link Vertx#vertx(VertxOptions)}, as no factory method is needed.
     *
     * @param options the NeonBee command line options
     * @param config  the {@link NeonBeeConfig} to use. If config is null the configuration gets loaded
     * @return the future to a new NeonBee instance initialized with default options and a new Vert.x instance
     */
    public static Future<NeonBee> create(NeonBeeOptions options, NeonBeeConfig config) {
        return create((OwnVertxFactory) (vertxOptions) -> newVertx(vertxOptions, options), options, config);
    }

    @VisibleForTesting
    @SuppressWarnings({ "PMD.EmptyCatchBlock", "PMD.AvoidCatchingThrowable" })
    static Future<NeonBee> create(Function<VertxOptions, Future<Vertx>> vertxFactory, NeonBeeOptions options,
            NeonBeeConfig config) {
        try {
            // create the NeonBee working and logging directory (as the only mandatory directory for NeonBee)
            Files.createDirectories(options.getLogDirectory());
        } catch (IOException e) {
            // nothing to do here, we can also (at least try) to work w/o a working directory
            // we should discuss if NeonBee can run in general without a working dir or not
        }

        VertxOptions vertxOptions = new VertxOptions().setEventLoopPoolSize(options.getEventLoopPoolSize())
                .setWorkerPoolSize(options.getWorkerPoolSize());

        CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();
        HazelcastClusterManager clusterManager =
                options.isClustered() ? new HazelcastClusterManager(options.getClusterConfig()) : null;
        vertxOptions.setMetricsOptions(
                new MicrometerMetricsOptions().setMicrometerRegistry(compositeMeterRegistry).setEnabled(true));
        if (options.isClustered()) {
            vertxOptions.setClusterManager(clusterManager);
        }

        // create a Vert.x instance (clustered or unclustered)
        return vertxFactory.apply(vertxOptions).compose(vertx -> {
            // at this point at any failure that occurs, it is in our responsibility to properly close down the created
            // Vert.x instance again. we have to be vigilant the fact that a runtime exception could happen anytime!
            Function<Throwable, Future<Void>> closeVertx = throwable -> {
                if (!(vertxFactory instanceof OwnVertxFactory)) {
                    // the Vert.x instance is *not* owned by us, thus don't close it either
                    LOGGER.error("Failure during bootstrap phase.", throwable); // NOPMD slf4j
                    return failedFuture(throwable);
                }

                // the instance has been created, but after initialization some post-initialization
                // tasks went wrong, stop Vert.x again. This will also call the close hook and clean up.
                LOGGER.error("Failure during bootstrap phase. Shutting down Vert.x instance.", throwable);

                // we wait for Vert.x to close, before we propagate the reason why booting failed
                return vertx.close().transform(closeResult -> failedFuture(throwable));
            };

            try {
                Future<NeonBeeConfig> neonBeeConfigFuture;
                if (config == null) {
                    neonBeeConfigFuture = loadConfig(vertx, options.getConfigDirectory());
                } else {
                    neonBeeConfigFuture = succeededFuture(config);
                }

                Future<NeonBee> neonBeeFuture = neonBeeConfigFuture
                        // create a NeonBee instance, hook registry and close handler
                        .map(c -> new NeonBee(vertx, options, c, compositeMeterRegistry, clusterManager));
                // boot NeonBee, on failure close Vert.x
                return neonBeeFuture.compose(NeonBee::boot).recover(closeVertx).compose(unused -> neonBeeFuture);
            } catch (Throwable t) {
                // on any exception (e.g. during the initialization of a NeonBee object) don't forget to close Vert.x!
                return closeVertx.apply(t).mapEmpty();
            }
        });
    }

    @VisibleForTesting
    static Future<Vertx> newVertx(VertxOptions vertxOptions, NeonBeeOptions options) {
        if (!options.isClustered()) {
            return succeededFuture(Vertx.vertx(vertxOptions));
        }

        vertxOptions.getEventBusOptions().setPort(options.getClusterPort());
        Optional.ofNullable(getHostIp()).filter(Predicate.not(String::isEmpty))
                .ifPresent(currentIp -> vertxOptions.getEventBusOptions().setHost(currentIp));
        return Vertx.clusteredVertx(vertxOptions).onFailure(throwable -> {
            LOGGER.error("Failed to start clustered Vert.x", throwable); // NOPMD slf4j
        });
    }

    private Future<Void> boot() {
        LOGGER.info("Booting NeonBee (ID: {})", nodeId);
        return registerHooks().compose(nothing -> hookRegistry.executeHooks(HookType.BEFORE_BOOTSTRAP))
                .onSuccess(anything -> {
                    // set the default timezone and overwrite any configured user.timezone property
                    TimeZone.setDefault(TimeZone.getTimeZone(config.getTimeZone()));

                    // further synchronous initializations which should happen before verticles are getting deployed
                }).compose(nothing -> all(initializeSharedMaps(), decorateEventBus(), createMicrometerRegistries()))
                .compose(nothing -> all(deployVerticles(), deployModules())) // deployment of verticles & modules
                .compose(nothing -> registerHealthChecks())
                .compose(nothing -> hookRegistry.executeHooks(HookType.AFTER_STARTUP))
                .onSuccess(result -> LOGGER.info("Successfully booted NeonBee (ID: {}})!", nodeId)).mapEmpty();
    }

    /**
     * Registers default NeonBee health checks to the {@link HealthCheckRegistry}.
     *
     * @return a Future
     */
    @VisibleForTesting
    Future<Void> registerHealthChecks() {
        List<Future<HealthCheck>> healthChecks = new ArrayList<>();

        if (Optional.ofNullable(config.getHealthConfig()).map(HealthConfig::isEnabled).orElse(true)) {
            healthChecks.add(healthRegistry.register(new MemoryHealthCheck(this)));
            healthChecks.add(healthRegistry.register(new EventLoopHealthCheck(this)));

            if (options.isClustered()) {
                healthChecks.add(healthRegistry.register(new HazelcastClusterHealthCheck(this, clusterManager)));
            }

            ServiceLoader.load(HealthCheckProvider.class).forEach(provider -> provider.get(vertx).forEach(check -> {
                if (!healthRegistry.getHealthChecks().containsKey(check.getId())) {
                    healthChecks.add(healthRegistry.register(check));
                }
            }));
        }

        return joinComposite(healthChecks).recover(v -> {
            healthChecks.stream().filter(Future::failed).map(Future::cause).forEach(t -> {
                LOGGER.error("Failed to register health checks to registry.", t);
            });
            return succeededFuture();
        }).mapEmpty();
    }

    private Future<Void> registerHooks() {
        if (options.shouldIgnoreClassPath()) {
            return succeededFuture();
        }

        return new HookScanner().scanForHooks(vertx)
                .compose(hookClasses -> allComposite(
                        hookClasses.stream().map(hookClass -> hookRegistry.registerHooks(hookClass, CORRELATION_ID))
                                .collect(Collectors.toList())).mapEmpty());
    }

    /**
     * Initializes a NeonBee local and cluster-wide map for shared usage across NeonBee.
     *
     * @return a future to indicate the result getting a shared async. map
     */
    @VisibleForTesting
    Future<Void> initializeSharedMaps() {
        SharedDataAccessor sharedData = new SharedDataAccessor(vertx, NeonBee.class);
        sharedLocalMap = sharedData.getLocalMap(SHARED_MAP_NAME);
        return sharedData.<String, Object>getAsyncMap(SHARED_MAP_NAME).onSuccess(asyncMap -> sharedAsyncMap = asyncMap)
                .mapEmpty();
    }

    /**
     * Decorates the event bus with in- & outbound interceptors for tracking and register any codecs that come bundled
     * with NeonBee, or that are configured in the NeonBee configuration.
     *
     * @return a future of the result of the registration (cannot fail currently)
     */
    @VisibleForTesting
    Future<Void> decorateEventBus() {
        return AsyncHelper.executeBlocking(vertx, () -> {
            TrackingDataHandlingStrategy strategy;

            try {
                strategy = (TrackingDataHandlingStrategy) Class.forName(config.getTrackingDataHandlingStrategy())
                        .getConstructor().newInstance();
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Failed to load configured tracking handling strategy {}. Use default.",
                            config.getTrackingDataHandlingStrategy(), e);
                }
                strategy = new TrackingDataLoggingStrategy();
            }

            vertx.eventBus().addInboundInterceptor(new TrackingInterceptor(MessageDirection.INBOUND, strategy))
                    .addOutboundInterceptor(new TrackingInterceptor(MessageDirection.OUTBOUND, strategy));

            // add any default system codecs (bundled w/ NeonBee) here
            vertx.eventBus().registerDefaultCodec(DataQuery.class, new DataQueryMessageCodec())
                    .registerDefaultCodec(EntityWrapper.class, new EntityWrapperMessageCodec(vertx))
                    .registerDefaultCodec(ImmutableBuffer.class, new ImmutableBufferMessageCodec())
                    .registerDefaultCodec(ImmutableJsonArray.class, new ImmutableJsonArrayMessageCodec())
                    .registerDefaultCodec(ImmutableJsonObject.class, new ImmutableJsonObjectMessageCodec())
                    .registerDefaultCodec(DataException.class, new DataExceptionMessageCodec());

            // add any additional default codecs configured in NeonBeeConfig
            config.getEventBusCodecs().forEach(this::registerCodec);
        });
    }

    /**
     * Registers a specific codec using the class name of the class to register the codec for and the class name of the
     * codec.
     *
     * @param className      the class name of the class to register the codec for
     * @param codecClassName the class name of the codec
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void registerCodec(String className, String codecClassName) {
        try {
            vertx.eventBus().registerDefaultCodec(Class.forName(className),
                    (MessageCodec) Class.forName(codecClassName).getConstructor().newInstance());
        } catch (Exception e) {
            LOGGER.warn("Failed to register codec {} for class {}", codecClassName, className, e);
        }
    }

    /**
     * Creates configured Micrometer registries and adds them to the composite meter registry of NeonBee.
     *
     * @return a future indicating the result of the operation
     */
    @VisibleForTesting
    Future<Void> createMicrometerRegistries() {
        return all(config.createMicrometerRegistries(vertx).collect(Collectors.toList()))
                .onSuccess(h -> h.<MeterRegistry>list().forEach(compositeMeterRegistry::add)).mapEmpty();
    }

    /**
     * Deploy any verticle (bundled, class path, etc.).
     *
     * @return a composite future about the result of the deployment
     */
    private Future<Void> deployVerticles() {
        Collection<NeonBeeProfile> activeProfiles = options.getActiveProfiles();
        if (LOGGER.isInfoEnabled()) {
            if (!activeProfiles.isEmpty()) {
                LOGGER.info("Deploying verticle with active profiles: {}", // NOPMD log guard false positive
                        activeProfiles.stream().map(NeonBeeProfile::name).collect(Collectors.joining(", ")));
            } else {
                LOGGER.info("No active profiles, only deploying system verticles");
            }
        }

        List<Future<?>> deployFutures = new ArrayList<>();

        // all system verticles (model refresher, deployer verticle, metrics verticle, etc.)
        deployFutures.add(deploySystemVerticles());

        // the server verticle, if the web profile is active
        if (NeonBeeProfile.WEB.isActive(activeProfiles)) {
            deployFutures.add(deployServerVerticle());
        }

        // verticles from class-path with a @NeonBeeDeployable annotation
        deployFutures.add(deployClassPathVerticles());

        return allComposite(deployFutures).mapEmpty();
    }

    /**
     * Deploy any system verticle (bundled w/ NeonBee).
     *
     * @return a future indicating the deployment of all system verticles
     */
    @SuppressWarnings("deprecation")
    private Future<Void> deploySystemVerticles() {
        List<Future<? extends Deployable>> requiredVerticles = new ArrayList<>();
        requiredVerticles.add(fromClass(vertx, ConsolidationVerticle.class, new JsonObject().put("instances", 1)));
        requiredVerticles.add(fromVerticle(vertx, new MetricsVerticle(1, TimeUnit.SECONDS)));
        requiredVerticles.add(fromVerticle(vertx, new HealthCheckVerticle()));
        requiredVerticles.add(fromClass(vertx, LoggerManagerVerticle.class));

        List<Future<Optional<? extends Deployable>>> optionalVerticles = new ArrayList<>();
        optionalVerticles.add(deployableWatchVerticle(options.getModelsDirectory(), ModelRefreshVerticle::new));
        optionalVerticles.add(deployableWatchVerticle(options.getVerticlesDirectory(), DeployerVerticle::new));
        optionalVerticles.add(deployableWatchVerticle(options.getModulesDirectory(), DeployerVerticle::new));

        LOGGER.info("Deploying system verticles ...");
        return allComposite(List.of(fromDeployables(requiredVerticles).compose(allTo(this)),
                allComposite(optionalVerticles).map(CompositeFuture::list).map(optionals -> {
                    return optionals.stream().map(Optional.class::cast).filter(Optional::isPresent).map(Optional::get)
                            .map(Deployable.class::cast).collect(Collectors.toList());
                }).map(Deployables::new).compose(anyTo(this)))).mapEmpty();
    }

    private Future<Optional<? extends Deployable>> deployableWatchVerticle(Path dirPath,
            Function<Path, ? extends Verticle> verticleFactory) {
        if (options.doNotWatchFiles()) {
            return succeededFuture(Optional.empty());
        }

        return FileSystemHelper.exists(vertx, dirPath).compose(exists -> {
            if (!exists) {
                if (LOGGER.isWarnEnabled()) {
                    String dirName = dirPath.getFileName().toString();
                    LOGGER.warn("No " + dirName + " directory, " + dirName + " are not being watched");
                }
                return succeededFuture(Optional.empty());
            }
            return fromVerticle(vertx, verticleFactory.apply(dirPath)).map(Optional::of);
        });
    }

    /**
     * Deploy the server verticle handling the endpoints.
     *
     * @return the future indicating the deployment of the server verticle
     */
    private Future<Void> deployServerVerticle() {
        LOGGER.info("Deploying server verticle ...");
        return fromClass(vertx, ServerVerticle.class, new JsonObject().put("instances", NUMBER_DEFAULT_INSTANCES))
                .compose(deployable -> deployable.deploy(this)).mapEmpty();
    }

    /**
     * Deploy any annotated verticle on the class path (not bundled w/ NeonBee, e.g. during development).
     *
     * @return a future indicating the deployment of the verticles
     */
    private Future<Void> deployClassPathVerticles() {
        if (options.shouldIgnoreClassPath()) {
            return succeededFuture();
        }

        LOGGER.info("Deploying verticle(s) from class path ...");
        return scanForDeployableClasses(vertx).compose(deployableClasses -> fromDeployables(deployableClasses.stream()
                .filter(verticleClass -> filterByAutoDeployAndProfiles(verticleClass, options.getActiveProfiles()))
                .map(verticleClass -> fromClass(vertx, verticleClass)).collect(Collectors.toList())))
                .onSuccess(deployables -> {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Deploy class path verticle(s) {}.", deployables.getIdentifier());
                    }
                }).compose(allTo(this)).mapEmpty();
    }

    @VisibleForTesting
    static boolean filterByAutoDeployAndProfiles(Class<? extends Verticle> verticleClass,
            Collection<NeonBeeProfile> activeProfiles) {
        NeonBeeDeployable annotation = verticleClass.getAnnotation(NeonBeeDeployable.class);
        return annotation.autoDeploy() && annotation.profile().isActive(activeProfiles);
    }

    /**
     * Deploy any modules defined in the NeonBee options.
     *
     * @return a future indicating the deployment of all modules
     */
    private Future<Void> deployModules() {
        List<Path> moduleJarPaths = options.getModuleJarPaths();
        if (moduleJarPaths.isEmpty()) {
            return succeededFuture();
        }

        LOGGER.info("Deploying module(s) ...");
        return fromDeployables(moduleJarPaths.stream().map(moduleJarPath -> fromJar(vertx, moduleJarPath))
                .collect(Collectors.toList())).compose(allTo(this)).mapEmpty();
    }

    @VisibleForTesting
    NeonBee(Vertx vertx, NeonBeeOptions options, NeonBeeConfig config, CompositeMeterRegistry compositeMeterRegistry,
            HazelcastClusterManager clusterManager) {
        this.vertx = vertx;
        this.options = options;
        this.config = config;

        this.clusterManager = clusterManager;
        this.healthRegistry = new HealthCheckRegistry(vertx);
        this.modelManager = new EntityModelManager(this);
        this.compositeMeterRegistry = compositeMeterRegistry;

        // to be able to retrieve the NeonBee instance from any point you have a Vert.x instance add it to a global map
        NEONBEE_INSTANCES.put(vertx, this);
        this.hookRegistry = new DefaultHookRegistry(vertx);
        registerCloseHandler(vertx);
    }

    @VisibleForTesting
    static Future<NeonBeeConfig> loadConfig(Vertx vertx, Path configPath) {
        LOGGER.info("Loading NeonBee configuration ...");
        return NeonBeeConfig.load(vertx, configPath).onSuccess(config -> {
            LOGGER.info("Successfully loaded NeonBee configuration");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Loaded configuration {}", config);
            }
        }).onFailure(throwable -> LOGGER.error("Failed to load NeonBee configuration", throwable));
    }

    @SuppressWarnings("rawtypes")
    private void registerCloseHandler(Vertx vertx) {
        try {
            // unfortunately the addCloseHook method is public, but hidden in VertxImpl. As we need to know when the
            // instance shuts down, register a close hook using reflections (might fail due to a SecurityManager)
            vertx.getClass().getMethod("addCloseHook", Closeable.class).invoke(vertx, (Closeable) handler -> {
                /*
                 * Called when Vert.x instance is closed, perform shut-down operations here
                 */
                handler.handle(
                        hookRegistry.executeHooks(HookType.BEFORE_SHUTDOWN).compose(shutdownHooksExecutionOutcomes -> {
                            if (shutdownHooksExecutionOutcomes.failed()) {
                                shutdownHooksExecutionOutcomes.<Future>list().stream().filter(Future::failed).forEach(
                                        future -> LOGGER.error("Shutdown hook execution failed", future.cause())); // NOPMD
                            }
                            NEONBEE_INSTANCES.remove(vertx);
                            return succeededFuture();
                        }).mapEmpty());
            });
        } catch (Exception e) {
            LOGGER.warn("Failed to register NeonBee close hook to Vert.x", e);
        }
    }

    /**
     * Returns the underlying Vert.x instance of NeonBee.
     *
     * @return the Vert.x instance
     */
    public Vertx getVertx() {
        return vertx;
    }

    /**
     * Returns the the (command-line) options.
     *
     * @return the (command-line) options
     */
    public NeonBeeOptions getOptions() {
        return options;
    }

    /**
     * Returns the NeonBee configuration.
     *
     * @return the NeonBee configuration
     */
    public NeonBeeConfig getConfig() {
        return config;
    }

    /**
     * Returns a local shared map shared within whole NeonBee instance.
     *
     * @return the local map
     */
    public LocalMap<String, Object> getLocalMap() {
        return sharedLocalMap;
    }

    /**
     * Returns an async. shared map shared within the NeonBee cluster (if not clustered, a local async. map).
     *
     * @return an async. shared map
     */
    public AsyncMap<String, Object> getAsyncMap() {
        return sharedAsyncMap;
    }

    /**
     * Returns the hook registry associated to the NeonBee instance.
     *
     * @return the hook registry
     */
    public HookRegistry getHookRegistry() {
        return hookRegistry;
    }

    /**
     * Returns whether an instance of the target verticle is available in local VM.
     *
     * @param targetVerticle target verticle address
     * @return whether an instance of the target verticle is available in local VM
     */
    public boolean isLocalConsumerAvailable(String targetVerticle) {
        return localConsumers.contains(targetVerticle);
    }

    /**
     * Registers a verticle as local consumer.
     *
     * @param verticleAddress verticle address
     */
    public void registerLocalConsumer(String verticleAddress) {
        localConsumers.add(verticleAddress);
    }

    /**
     * Unregisters a verticle as local consumer.
     *
     * @param verticleAddress verticle address
     */
    public void unregisterLocalConsumer(String verticleAddress) {
        localConsumers.remove(verticleAddress);
    }

    /**
     * Returns the ServerConfig if NeonBee is started with WEB profile.
     *
     * @return the ServerConfig or null if ServerVerticle is not started.
     */
    public ServerConfig getServerConfig() {
        return new ServerConfig((JsonObject) getLocalMap().get(ServerVerticle.SERVER_CONFIG_KEY));
    }

    /**
     * Get the {@link EntityModelManager}.
     *
     * @return the {@link EntityModelManager}
     */
    public EntityModelManager getModelManager() {
        return modelManager;
    }

    /**
     * Get the {@link CompositeMeterRegistry}.
     *
     * @return the {@link CompositeMeterRegistry}
     */
    public CompositeMeterRegistry getCompositeMeterRegistry() {
        return compositeMeterRegistry;
    }

    /**
     * Get a unique identifier of the node NeonBee is running on.
     *
     * @return the id
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Get the health check registry associated to the NeonBee instance.
     *
     * @return an instance of {@link HealthCheckRegistry}
     */
    public HealthCheckRegistry getHealthCheckRegistry() {
        return healthRegistry;
    }

    /**
     * Hidden marker function interface, that indicates to the boot-stage that an own Vert.x instance was created, and
     * we must be held responsible to close it again.
     */
    @VisibleForTesting
    interface OwnVertxFactory extends Function<VertxOptions, Future<Vertx>> {}
}
