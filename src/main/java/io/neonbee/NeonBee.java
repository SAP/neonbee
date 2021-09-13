package io.neonbee;

import static ch.qos.logback.classic.util.ContextInitializer.CONFIG_FILE_PROPERTY;
import static io.neonbee.internal.helper.AsyncHelper.allComposite;
import static io.neonbee.internal.helper.HostHelper.getHostIp;
import static io.neonbee.internal.scanner.DeployableScanner.scanForDeployableClasses;
import static io.vertx.core.CompositeFuture.all;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.System.setProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.config.NeonBeeConfig;
import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.hook.HookRegistry;
import io.neonbee.hook.HookType;
import io.neonbee.hook.internal.DefaultHookRegistry;
import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.internal.buffer.ImmutableBuffer;
import io.neonbee.internal.codec.DataQueryMessageCodec;
import io.neonbee.internal.codec.EntityWrapperMessageCodec;
import io.neonbee.internal.codec.ImmutableBufferMessageCodec;
import io.neonbee.internal.codec.ImmutableJsonArrayMessageCodec;
import io.neonbee.internal.codec.ImmutableJsonObjectMessageCodec;
import io.neonbee.internal.deploy.Deployable;
import io.neonbee.internal.deploy.Deployment;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.internal.json.ImmutableJsonArray;
import io.neonbee.internal.json.ImmutableJsonObject;
import io.neonbee.internal.scanner.HookScanner;
import io.neonbee.internal.tracking.MessageDirection;
import io.neonbee.internal.tracking.TrackingDataHandlingStrategy;
import io.neonbee.internal.tracking.TrackingDataLoggingStrategy;
import io.neonbee.internal.tracking.TrackingInterceptor;
import io.neonbee.internal.verticle.ConsolidationVerticle;
import io.neonbee.internal.verticle.DeployerVerticle;
import io.neonbee.internal.verticle.LoggerManagerVerticle;
import io.neonbee.internal.verticle.MetricsVerticle;
import io.neonbee.internal.verticle.ModelRefreshVerticle;
import io.neonbee.internal.verticle.ServerVerticle;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.vertx.core.Closeable;
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
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class NeonBee {
    /** // @formatter:off
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

    @VisibleForTesting
    static final String CORRELATION_ID = "Initializing-NeonBee";

    @VisibleForTesting
    static Logger logger;

    private static final Map<Vertx, NeonBee> NEONBEE_INSTANCES = new HashMap<>();

    // Attention DO NOT create a static LOGGER instance here! NeonBee needs to start up first, in order to set the right
    // logging parameterization, like the logging configuration, and the internal loggers for Netty.
    private static final String HAZELCAST_LOGGING_TYPE = "hazelcast.logging.type";

    private static final String LOG_DIR_PROPERTY = "LOG_DIR";

    private static final String SHARED_MAP_NAME = "#sharedMap";

    private static final int NUMBER_DEFAULT_INSTANCES = 4;

    @VisibleForTesting
    NeonBeeConfig config;

    private final Vertx vertx;

    private final NeonBeeOptions options;

    private final HookRegistry hookRegistry;

    private LocalMap<String, Object> sharedLocalMap;

    private AsyncMap<String, Object> sharedAsyncMap;

    private final Set<String> localConsumers = new ConcurrentHashSet<>();

    /**
     * Convenience method for returning the current NeonBee instance.
     * <p>
     * Important: Will only return a value in case a Vert.x context is available, otherwise returns null. Attention:
     * This method is NOT signature compliant to {@link Vertx#vertx()}! It will NOT create a new NeonBee instance,
     * please use {@link NeonBee#create(NeonBeeOptions)} or {@link NeonBee#create(Supplier, NeonBeeOptions)} instead.
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
        return create(() -> newVertx(options), options);
    }

    @VisibleForTesting
    @SuppressWarnings("PMD.EmptyCatchBlock")
    static Future<NeonBee> create(Supplier<Future<Vertx>> vertxFutureSupplier, NeonBeeOptions options) {
        try {
            // create the NeonBee working and logging directory (as the only mandatory directory for NeonBee)
            Files.createDirectories(options.getLogDirectory());
        } catch (IOException e) {
            // nothing to do here, we can also (at least try) to work w/o a working directory
            // we should discuss if NeonBee can run in general without a working dir or not
        }

        // switch to the SLF4J logging facade (using Logback as a logging backend). It is required to set the logging
        // system properties before the first logger is initialized, so do it before the Vert.x initialization.
        setProperty(CONFIG_FILE_PROPERTY, options.getConfigDirectory().resolve("logback.xml").toString());
        setProperty(HAZELCAST_LOGGING_TYPE, "slf4j");
        setProperty(LOG_DIR_PROPERTY, options.getLogDirectory().toAbsolutePath().toString());
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
        logger = LoggerFactory.getLogger(NeonBee.class);

        // create a Vert.x instance (clustered or unclustered)
        return vertxFutureSupplier.get().compose(vertx -> {
            // create a NeonBee instance, load the configuration and boot it up
            NeonBee neonBee = new NeonBee(vertx, options);

            return neonBee.loadConfig().compose(config -> neonBee.boot()).map(neonBee);
        });
    }

    @VisibleForTesting
    static Future<Vertx> newVertx(NeonBeeOptions options) {
        VertxOptions vertxOptions = new VertxOptions().setEventLoopPoolSize(options.getEventLoopPoolSize())
                .setWorkerPoolSize(options.getWorkerPoolSize()).setMetricsOptions(new MicrometerMetricsOptions()
                        .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true)).setEnabled(true));

        if (!options.isClustered()) {
            return succeededFuture(Vertx.vertx(vertxOptions));
        } else {
            vertxOptions.setClusterManager(new HazelcastClusterManager(options.getClusterConfig())).getEventBusOptions()
                    .setPort(options.getClusterPort());
            Optional.ofNullable(getHostIp()).filter(Predicate.not(String::isEmpty))
                    .ifPresent(currentIp -> vertxOptions.getEventBusOptions().setHost(currentIp));
            return Vertx.clusteredVertx(vertxOptions).onFailure(throwable -> {
                logger.error("Failed to start clustered Vert.x", throwable); // NOPMD slf4j
            });
        }
    }

    private Future<Void> boot() {
        return registerHooks().compose(nothing -> hookRegistry.executeHooks(HookType.BEFORE_BOOTSTRAP))
                .compose(anything -> {
                    // set the default timezone and overwrite any configured user.timezone property
                    TimeZone.setDefault(TimeZone.getTimeZone(config.getTimeZone()));

                    // decorate the event bus with in- & outbound interceptors for tracking
                    decorateEventBus();

                    return all(initializeSharedDataAccessor(), registerCodecs()).compose(nothing -> deployVerticles())
                            .onFailure(throwable -> {
                                // the instance has been created, but after initialization some post-initialization
                                // tasks went wrong, stop Vert.x again. This will also call the close hook and clean up
                                logger.error("Failure during bootstrap phase. Shutting down Vert.x instance.",
                                        throwable);
                                vertx.close();
                            }).compose(nothing -> hookRegistry.executeHooks(HookType.AFTER_STARTUP));
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

    @VisibleForTesting
    void decorateEventBus() {
        TrackingDataHandlingStrategy strategy;

        try {
            strategy = (TrackingDataHandlingStrategy) Class.forName(config.getTrackingDataHandlingStrategy())
                    .getConstructor().newInstance();
        } catch (Exception e) {
            logger.warn("Failed to load configured tracking handling strategy {}. Use default.",
                    config.getTrackingDataHandlingStrategy(), e);
            strategy = new TrackingDataLoggingStrategy();
        }

        vertx.eventBus().addInboundInterceptor(new TrackingInterceptor(MessageDirection.INBOUND, strategy))
                .addOutboundInterceptor(new TrackingInterceptor(MessageDirection.OUTBOUND, strategy));
    }

    private Future<Void> initializeSharedDataAccessor() {
        return succeededFuture(new SharedDataAccessor(vertx, NeonBee.class))
                .compose(sharedData -> AsyncHelper.executeBlocking(vertx, promise -> {
                    sharedLocalMap = sharedData.getLocalMap(SHARED_MAP_NAME);
                    sharedData.<String, Object>getAsyncMap(SHARED_MAP_NAME, asyncResult -> {
                        sharedAsyncMap = asyncResult.result();
                        promise.handle(asyncResult.mapEmpty());
                    });
                }));
    }

    /**
     * Register any codecs (bundled with NeonBee, or configured in the NeonBee options).
     *
     * @return a future of the result of the registration (cannot fail currently)
     */
    private Future<Void> registerCodecs() {
        // add any default system codecs (bundled w/ NeonBee) here
        vertx.eventBus().registerDefaultCodec(DataQuery.class, new DataQueryMessageCodec())
                .registerDefaultCodec(EntityWrapper.class, new EntityWrapperMessageCodec(vertx))
                .registerDefaultCodec(ImmutableBuffer.class, new ImmutableBufferMessageCodec())
                .registerDefaultCodec(ImmutableJsonArray.class, new ImmutableJsonArrayMessageCodec())
                .registerDefaultCodec(ImmutableJsonObject.class, new ImmutableJsonObjectMessageCodec());

        // add any additional default codecs (configured in NeonBeeOptions) here
        getConfig().getEventBusCodecs().forEach(this::registerCodec);

        return succeededFuture();
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
            logger.warn("Failed to register codec {} for class {}", codecClassName, className, e);
        }
    }

    /**
     * Deploy any verticle (bundled, class path, etc.).
     *
     * @return a composite future about the result of the deployment
     */
    private Future<Void> deployVerticles() {
        List<NeonBeeProfile> activeProfiles = options.getActiveProfiles();
        logger.info("Deploying verticle with active profiles: {}",
                activeProfiles.stream().map(NeonBeeProfile::name).collect(Collectors.joining(",")));

        List<Future<?>> deployFutures = new ArrayList<>(deploySystemVerticles());
        if (NeonBeeProfile.WEB.isActive(activeProfiles)) {
            deployFutures.add(deployServerVerticle());
        }

        deployFutures.add(deployClassPathVerticles());
        return allComposite(deployFutures).map((Void) null);
    }

    /**
     * Deploy the server verticle handling the endpoints.
     *
     * @return the future deploying the server verticle
     */
    private Future<String> deployServerVerticle() {
        logger.info("Deploy server verticle");

        return Deployable
                .fromClass(vertx, ServerVerticle.class, CORRELATION_ID,
                        new JsonObject().put("instances", NUMBER_DEFAULT_INSTANCES))
                .compose(deployable -> deployable.deploy(vertx, CORRELATION_ID).future())
                .map(Deployment::getDeploymentId);
    }

    /**
     * Deploy any system verticle (bundled w/ Neonbee).
     *
     * @return a list of futures deploying verticle
     */
    private List<Future<String>> deploySystemVerticles() {
        // any non-configurable system verticles may be deployed with a simple vertx.deployVerticle call, while
        // configurable verticles (that might come with an own config file) have to use the Deployable interface

        logger.info("Deploying system verticles...");
        List<Future<String>> systemVerticles = new ArrayList<>();

        systemVerticles.add(
                vertx.deployVerticle(new ModelRefreshVerticle(options.getModelsDirectory())).otherwise(throwable -> {
                    // non-fatal exception, in case this fails, NeonBee is still able to run!
                    logger.warn("ModelRefreshVerticle was not deployed. Models directory is not being watched!",
                            throwable);
                    return null;
                }));

        systemVerticles.add(Deployable
                .fromVerticle(vertx, new DeployerVerticle(options.getVerticlesDirectory()), CORRELATION_ID, null)
                .compose(deployable -> deployable.deploy(vertx, CORRELATION_ID).future())
                .map(Deployment::getDeploymentId).otherwise(throwable -> {
                    // non-fatal exception, in case this fails, NeonBee is still able to run!
                    logger.warn("DeployerVerticle was not deployed. Verticles directory is not being watched!",
                            throwable);
                    return null;
                }));

        systemVerticles.add(Deployable
                .fromClass(vertx, ConsolidationVerticle.class, CORRELATION_ID, new JsonObject().put("instances", 1))
                .compose(deployable -> deployable.deploy(vertx, CORRELATION_ID).future())
                .map(Deployment::getDeploymentId));

        systemVerticles.add(vertx.deployVerticle(new MetricsVerticle(1, TimeUnit.SECONDS)));
        systemVerticles.add(vertx.deployVerticle(new LoggerManagerVerticle()));

        return systemVerticles;
    }

    /**
     * Deploy any annotated verticle on the class path (not bundled w/ NeonBee, e.g. during development)
     *
     * @return a list of futures deploying verticle
     */
    private Future<Void> deployClassPathVerticles() {
        if (options.shouldIgnoreClassPath()) {
            return succeededFuture();
        }

        return scanForDeployableClasses(vertx).compose(deployableClasses -> {
            List<Class<? extends Verticle>> filteredVerticleClasses = deployableClasses.stream()
                    .filter(verticleClass -> filterByAutoDeployAndProfiles(verticleClass, options.getActiveProfiles()))
                    .collect(Collectors.toList());
            logger.info("Deploy classpath verticle {}.",
                    filteredVerticleClasses.stream().map(Class::getCanonicalName).collect(Collectors.joining(",")));
            return allComposite(filteredVerticleClasses.stream()
                    .map(verticleClass -> Deployable.fromClass(vertx, verticleClass, CORRELATION_ID, null)
                            .compose(deployable -> deployable.deploy(vertx, CORRELATION_ID).future())
                            .map(Deployment::getDeploymentId))
                    .collect(Collectors.toList()));
        }).mapEmpty();
    }

    @VisibleForTesting
    static boolean filterByAutoDeployAndProfiles(Class<? extends Verticle> verticleClass,
            List<NeonBeeProfile> activeProfiles) {
        NeonBeeDeployable annotation = verticleClass.getAnnotation(NeonBeeDeployable.class);
        return annotation.autoDeploy() && annotation.profile().isActive(activeProfiles);
    }

    @VisibleForTesting
    NeonBee(Vertx vertx, NeonBeeOptions options) {
        this.vertx = vertx;
        this.options = options;

        // to be able to retrieve the NeonBee instance from any point you have a Vert.x instance add it to a global map
        NEONBEE_INSTANCES.put(vertx, this);
        this.hookRegistry = new DefaultHookRegistry(vertx);
        registerCloseHandler(vertx);
    }

    private Future<NeonBeeConfig> loadConfig() {
        return NeonBeeConfig.load(vertx).onSuccess(config -> this.config = config);
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
                                        future -> logger.error("Shutdown hook execution failed", future.cause())); // NOPMD
                            }
                            NEONBEE_INSTANCES.remove(vertx);
                            return succeededFuture();
                        }).mapEmpty());
            });
        } catch (Exception e) {
            logger.warn("Failed to register NeonBee close hook to Vert.x", e);
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
}
