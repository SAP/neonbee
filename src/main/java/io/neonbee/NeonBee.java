package io.neonbee;

import static ch.qos.logback.classic.util.ContextInitializer.CONFIG_FILE_PROPERTY;
import static io.neonbee.internal.Helper.allComposite;
import static io.neonbee.internal.scanner.DeployableScanner.scanForDeployableClasses;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.System.setProperty;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.hook.HookRegistry;
import io.neonbee.hook.HookType;
import io.neonbee.hook.internal.DefaultHookRegistry;
import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.internal.codec.DataQueryMessageCodec;
import io.neonbee.internal.codec.EntityWrapperMessageCodec;
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
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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

    private static final int NUMBER_DEFAULT_INSTANCES = 16;

    private final Vertx vertx;

    private final NeonBeeOptions options;

    private final NeonBeeConfig config;

    private final HookRegistry hookRegistry;

    private LocalMap<String, Object> sharedLocalMap;

    private AsyncMap<String, Object> sharedAsyncMap;

    private final Set<String> localConsumers = new ConcurrentHashSet<>();

    @VisibleForTesting
    static Future<Vertx> initVertx(NeonBeeOptions options) {
        VertxOptions vertxOptions = new VertxOptions().setEventLoopPoolSize(options.getEventLoopPoolSize())
                .setWorkerPoolSize(options.getWorkerPoolSize()).setMetricsOptions(new MicrometerMetricsOptions()
                        .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true)).setEnabled(true));

        Promise<Vertx> promise = Promise.promise();
        if (options.isClustered()) {
            HazelcastInstance hzInstance = Hazelcast.newHazelcastInstance(options.getClusterConfig());
            vertxOptions.setClusterManager(new HazelcastClusterManager(hzInstance)).getEventBusOptions()
                    .setPort(options.getClusterPort());
            String currentIp = System.getenv("CF_INSTANCE_INTERNAL_IP");
            if (!Strings.isNullOrEmpty(currentIp)) {
                vertxOptions.getEventBusOptions().setHost(currentIp);
            }
            Vertx.clusteredVertx(vertxOptions, result -> {
                if (result.failed()) {
                    logger.error("Failed to start Vertx cluster", result.cause()); // NOPMD slf4j
                    promise.fail(result.cause());
                } else {
                    promise.complete(result.result());
                }
            });
        } else {
            promise.complete(Vertx.vertx(vertxOptions));
        }
        return promise.future();
    }

    /**
     * Convenience method for returning the current NeonBee instance.
     * <p>
     * Important: Will only return a value in case a Vert.x context is available, otherwise returns null. Attention:
     * This method is NOT signature compliant to {@link Vertx#vertx()}! It will NOT create a new NeonBee instance,
     * please use {@link NeonBee#instance(Handler)} or {@link NeonBee#instance(NeonBeeOptions, Handler)} instead.
     *
     * @return A NeonBee instance or null
     */
    public static NeonBee instance() {
        Context context = Vertx.currentContext();
        return context != null ? instance(context.owner()) : null;
    }

    /**
     * Get the NeonBee instance for any given Vert.x instance.
     *
     * @param vertx The Vert.x instance to get the NeonBee instance from
     * @return A NeonBee instance or null
     */
    public static NeonBee instance(Vertx vertx) {
        return NEONBEE_INSTANCES.get(vertx);
    }

    /**
     * Create a new NeonBee instance, with default options. Similar to the static {@link Vertx#vertx()} method.
     *
     * @param resultHandler the result handler which is called as soon as the NeonBee instance has been created or the
     *                      creation failed
     */
    public static void instance(Handler<AsyncResult<NeonBee>> resultHandler) {
        instance(new NeonBeeOptions.Mutable(), resultHandler);
    }

    /**
     * Create a new NeonBee instance, with the given options. Similar to the static Vert.x method.
     * <p>
     * Note: This method is NOT a static method like {@link Vertx#vertx(VertxOptions)}, as no factory method is needed.
     *
     * @param options       the NeonBee command line options
     * @param resultHandler the result handler which is called as soon as the NeonBee instance has been created or the
     *                      creation failed
     */
    public static void instance(NeonBeeOptions options, Handler<AsyncResult<NeonBee>> resultHandler) {
        instance(() -> initVertx(options), options, resultHandler);
    }

    @VisibleForTesting
    @SuppressWarnings("PMD.EmptyCatchBlock")
    static void instance(Supplier<Future<Vertx>> vertxFutureSupplier, NeonBeeOptions options,
            Handler<AsyncResult<NeonBee>> resultHandler) {

        try {
            // Create the NeonBee working and logging directory (as the only mandatory directory for NeonBee)
            Files.createDirectories(options.getLogDirectory());
        } catch (IOException e) {
            // nothing to do here, we can also (at least try) to work w/o a working directory
            // we should discuss if NeonBee can run in general without a working dir or not
        }

        // Switch to the SLF4J logging facade (using Logback as a logging backend). It is required to set the logging
        // system properties before the first logger is initialized, so do it before the Vert.x initialization.
        setProperty(CONFIG_FILE_PROPERTY, options.getConfigDirectory().resolve("logback.xml").toString());
        setProperty(HAZELCAST_LOGGING_TYPE, "slf4j");
        setProperty(LOG_DIR_PROPERTY, options.getLogDirectory().toAbsolutePath().toString());
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
        logger = LoggerFactory.getLogger(NeonBee.class);

        // Create a Vert.x instance (clustered or unclustered)
        vertxFutureSupplier.get().compose(vertx -> bootstrap(options, vertx)).onComplete(resultHandler);
    }

    private static Future<NeonBee> bootstrap(NeonBeeOptions options, Vertx vertx) {
        return succeededFuture(new NeonBee(vertx, options)).compose(neonBee -> {
            return neonBee.registerHooks()
                    .compose(v -> neonBee.getHookRegistry().executeHooks(HookType.BEFORE_BOOTSTRAP).mapEmpty())
                    .compose(v -> succeededFuture(decorateEventBus(neonBee)))
                    .compose(v -> initializeSharedDataAccessor(neonBee)).compose(v -> neonBee.registerCodecs())
                    .compose(v -> {
                        // Set the default TimeZone for date operations. This overwrites any configured
                        // user.timezone properties.
                        TimeZone.setDefault(TimeZone.getTimeZone(options.getTimeZoneId()));
                        return succeededFuture();
                    }).compose(v -> neonBee.deployVerticles()).recover(throwable -> {
                        // the instance has been created, but after initialization some post-initialization
                        // tasks went wrong, stop Vert.x again. This will also call the close hook and clean up
                        vertx.close();

                        return failedFuture(throwable); // propagate the failure
                    }).compose(v -> neonBee.getHookRegistry().executeHooks(HookType.AFTER_STARTUP).mapEmpty())
                    .map(neonBee);
        });
    }

    private static Future<Void> initializeSharedDataAccessor(NeonBee neonBee) {
        return succeededFuture(new SharedDataAccessor(neonBee.getVertx(), NeonBee.class))
                .compose(sharedData -> AsyncHelper.executeBlocking(neonBee.getVertx(), promise -> {
                    neonBee.sharedLocalMap = sharedData.getLocalMap(SHARED_MAP_NAME);
                    sharedData.<String, Object>getAsyncMap(SHARED_MAP_NAME, asyncResult -> {
                        neonBee.sharedAsyncMap = asyncResult.result();
                        promise.handle(asyncResult.mapEmpty());
                    });
                }));
    }

    private Future<Void> registerHooks() {
        if (options.shouldIgnoreClassPath()) {
            return succeededFuture();
        }

        List<Future<?>> hookRegistrations = new HookScanner().scanForHooks().stream()
                .map(hookClass -> hookRegistry.registerHooks(hookClass, CORRELATION_ID)).collect(Collectors.toList());
        return allComposite(hookRegistrations).mapEmpty();
    }

    @VisibleForTesting
    static Void decorateEventBus(NeonBee neonBee) {
        TrackingDataHandlingStrategy strategy;
        try {
            strategy = (TrackingDataHandlingStrategy) Class
                    .forName(neonBee.getConfig().getTrackingDataHandlingStrategy()).getConstructor().newInstance();
        } catch (Exception e) {
            logger.warn("Failed to load configured tracking handling strategy {}. Use default.",
                    neonBee.getConfig().getTrackingDataHandlingStrategy(), e);
            strategy = new TrackingDataLoggingStrategy();
        }
        neonBee.getVertx().eventBus().addInboundInterceptor(new TrackingInterceptor(MessageDirection.INBOUND, strategy))
                .addOutboundInterceptor(new TrackingInterceptor(MessageDirection.OUTBOUND, strategy));

        return null;
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

        List<Future<String>> deployFutures = new ArrayList<>(deploySystemVerticles());
        if (NeonBeeProfile.WEB.isActive(activeProfiles)) {
            deployFutures.addAll(deployWebVerticles());
        }

        deployFutures.addAll(deployClassPathVerticles());
        return allComposite(deployFutures).map((Void) null);
    }

    /**
     * Deploy any web verticle (bundled w/ NeonBee).
     *
     * @return a list of futures deploying verticle
     */
    private List<Future<String>> deployWebVerticles() {
        logger.info("Deploy web verticle.");

        return List.of(Deployable
                .fromClass(vertx, ServerVerticle.class, CORRELATION_ID,
                        new JsonObject().put("instances", NUMBER_DEFAULT_INSTANCES))
                .compose(deployable -> deployable.deploy(vertx, CORRELATION_ID).future())
                .map(Deployment::getDeploymentId));
    }

    /**
     * Deploy any system verticle (bundled w/ Neonbee).
     *
     * @return a list of futures deploying verticle
     */
    private List<Future<String>> deploySystemVerticles() {
        logger.info("Deploy system verticle.");
        List<Future<String>> systemVerticles = new ArrayList<>();
        // put any non-configurable system verticle here

        systemVerticles.add(Future
                .<String>future(asyncDeployment -> vertx
                        .deployVerticle(new ModelRefreshVerticle(options.getModelsDirectory()), asyncDeployment))
                .otherwise(throwable -> { // non-fatal exception, in case this fails, NeonBee is still able to run!
                    logger.warn("Could not deploy the ModelRefreshVerticle. Models directory is not being watched!",
                            throwable);
                    return null;
                }));

        systemVerticles.add(Deployable
                .fromVerticle(vertx, new DeployerVerticle(options.getVerticlesDirectory()), CORRELATION_ID, null)
                .compose(deployable -> deployable.deploy(vertx, CORRELATION_ID).future())
                .map(Deployment::getDeploymentId).otherwise(throwable -> {
                    // non-fatal exception, in case this fails, NeonBee is still able to run!
                    logger.warn("Could not deploy the DeployerVerticle. Verticles directory is not being watched!",
                            throwable);
                    return null;
                }));

        systemVerticles.add(Deployable
                .fromClass(vertx, ConsolidationVerticle.class, CORRELATION_ID, new JsonObject().put("instances", 1))
                .compose(deployable -> deployable.deploy(vertx, CORRELATION_ID).future())
                .map(Deployment::getDeploymentId));

        systemVerticles.add(Future.<String>future(
                asyncDeployment -> vertx.deployVerticle(new MetricsVerticle(1, TimeUnit.SECONDS), asyncDeployment)));

        systemVerticles
                .add(Future.<String>future(promise -> vertx.deployVerticle(new LoggerManagerVerticle(), promise)));

        return systemVerticles;
    }

    /**
     * Deploy any annotated verticle on the class path (not bundled w/ NeonBee, e.g. during development)
     *
     * @return a list of futures deploying verticle
     */
    private List<Future<String>> deployClassPathVerticles() {
        if (options.shouldIgnoreClassPath()) {
            return Collections.emptyList();
        }

        try {
            List<Class<? extends Verticle>> deployableClasses = scanForDeployableClasses();
            List<Class<? extends Verticle>> filteredVerticleClasses = deployableClasses.stream()
                    .filter(verticleClass -> filterByAutoDeployAndProfiles(verticleClass, options.getActiveProfiles()))
                    .collect(Collectors.toList());
            logger.info("Deploy classpath verticle {}.",
                    filteredVerticleClasses.stream().map(Class::getCanonicalName).collect(Collectors.joining(",")));
            return filteredVerticleClasses.stream()
                    .map(verticleClass -> Deployable.fromClass(vertx, verticleClass, CORRELATION_ID, null)
                            .compose(deployable -> deployable.deploy(vertx, CORRELATION_ID).future())
                            .map(Deployment::getDeploymentId))
                    .collect(Collectors.toList());
        } catch (IOException | URISyntaxException e) {
            return List.of(failedFuture(e));
        }
    }

    @VisibleForTesting
    static boolean filterByAutoDeployAndProfiles(Class<? extends Verticle> verticleClass,
            List<NeonBeeProfile> activeProfiles) {
        NeonBeeDeployable annotation = verticleClass.getAnnotation(NeonBeeDeployable.class);
        return annotation.autoDeploy() && annotation.profile().isActive(activeProfiles);
    }

    @VisibleForTesting
    NeonBee(Vertx vertx, NeonBeeOptions options) {
        this.options = options;
        this.vertx = vertx;

        // to be able to retrieve the NeonBee instance from any point you have a Vert.x instance add it to a global map
        NEONBEE_INSTANCES.put(vertx, this);
        this.hookRegistry = new DefaultHookRegistry(vertx);
        this.config = new NeonBeeConfig(vertx);
        registerCloseHandler(vertx);
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
                handler.handle(getHookRegistry().executeHooks(HookType.BEFORE_SHUTDOWN)
                        .compose(shutdownHooksExecutionOutcomes -> {
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
     * @param verticleAdresss verticle address
     */
    public void registerLocalConsumer(String verticleAdresss) {
        localConsumers.add(verticleAdresss);
    }

    /**
     * Unregisters a verticle as local consumer.
     *
     * @param verticleAdresss verticle address
     */
    public void unregisterLocalConsumer(String verticleAdresss) {
        localConsumers.remove(verticleAdresss);
    }
}
