package io.neonbee.test.base;

import static io.neonbee.NeonBeeExtension.DEFAULT_TIMEOUT_DURATION;
import static io.neonbee.NeonBeeExtension.DEFAULT_TIMEOUT_UNIT;
import static io.neonbee.NeonBeeProfile.NO_WEB;
import static io.neonbee.NeonBeeProfile.WEB;
import static io.neonbee.internal.helper.ConfigHelper.readConfig;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static io.neonbee.test.helper.WorkingDirectoryBuilder.readDeploymentOptions;
import static io.neonbee.test.helper.WorkingDirectoryBuilder.writeDeploymentOptions;
import static io.vertx.core.Future.failedFuture;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.NeonBeeMockHelper;
import io.neonbee.NeonBeeOptions;
import io.neonbee.NeonBeeProfile;
import io.neonbee.config.AuthHandlerConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.data.DataVerticle;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.deploy.DeployableVerticle;
import io.neonbee.internal.deploy.Deployment;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.job.JobVerticle;
import io.neonbee.test.helper.ConcurrentHelper;
import io.neonbee.test.helper.DeploymentHelper;
import io.neonbee.test.helper.DummyVerticleHelper;
import io.neonbee.test.helper.DummyVerticleHelper.DummyDataVerticleFactory;
import io.neonbee.test.helper.DummyVerticleHelper.DummyEntityVerticleFactory;
import io.neonbee.test.helper.FileSystemHelper;
import io.neonbee.test.helper.SystemHelper;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.neonbee.test.listeners.StaleVertxChecker;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class NeonBeeTestBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeonBeeTestBase.class);

    // create a dummy JsonObject indicating that the provideUserPrincipal method was not overridden. depending on the
    // test case the provideUserPrincipal method maybe should return null in some cases, thus we need another way to
    // check if we should deploy the dummy verticle. this private object can never be returned by anyone overriding the
    // method, thus if any other value than this is returned, we can be sure a sub-class overrode the method
    private static final JsonObject NO_USER_PRINCIPAL = new JsonObject();

    private Path workingDirPath;

    private NeonBee neonBee;

    private boolean isDummyServerVerticleDeployed;

    @BeforeEach
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @SuppressWarnings("ReferenceEquality")
    public void setUp(Vertx vertx, VertxTestContext testContext, TestInfo testInfo) throws Exception {
        // associate the Vert.x instance to the current test (unfortunately the only "identifier" that is shared between
        // TestInfo and TestIdentifier is the display name)
        StaleVertxChecker.VERTX_TEST_MAP.put(vertx, testInfo.getDisplayName());

        // build working directory
        workingDirPath = FileSystemHelper.createTempDirectory();
        provideWorkingDirectoryBuilder(testInfo, testContext).build(workingDirPath);

        // create a default set of options for NeonBee and adapt them if necessary
        NeonBeeOptions.Mutable options = defaultOptions();
        adaptOptions(testInfo, options);
        options.setWorkingDirectory(workingDirPath);

        // probe for a custom user principal
        AtomicBoolean customUserPrincipal = new AtomicBoolean(false);
        if (provideUserPrincipal(testInfo) != NO_USER_PRINCIPAL) { // do NOT use equals here! compare references!
            if (!WEB.isActive(options.getActiveProfiles())) {
                testContext.failNow(new IllegalStateException(
                        "A custom user principal can only be set, if the WEB profile is active!"));
                return;
            }

            // add the NO_WEB profile to the active profiles list, this way we won't have to undeploy the ServerVerticle
            // again later on and can deploy our dummy ServerVerticle right away
            options.addActiveProfile(NO_WEB);
            customUserPrincipal.set(true);
        }

        URL defaultLogbackConfig = Resources.getResource(NeonBeeTestBase.class, "NeonBeeTestBase-Logback.xml");
        try (InputStream is = Resources.asByteSource(defaultLogbackConfig).openStream()) {
            Files.copy(is, options.getConfigDirectory().resolve("logback.xml"));
        }

        Future<NeonBee> future = NeonBeeMockHelper.createNeonBee(vertx, options);

        // as documented here [1] in case there are multiple @BeforeEach methods (like in this test base and in
        // sub-classes of this test base) asynchronous operations will be executed in parallel, because other
        // @BeforeEach methods will not wait for this testContext to finish. rather JUnit will wait for the test to be
        // called for Vert.x to join all the testContexts together. for this reason we have to use a latch here.
        // [1] https://vertx.io/docs/vertx-junit5/java/#_warning_on_multiple_methods_for_the_same_lifecycle_events
        CountDownLatch latch = new CountDownLatch(1);
        future.onComplete(asyncNeonBee -> {
            if (asyncNeonBee.failed()) {
                testContext.failNow(asyncNeonBee.cause());
                latch.countDown();
            } else {
                neonBee = asyncNeonBee.result();

                if (customUserPrincipal.get()) {
                    // use a dummy ServerVerticle that returns the principal by calling the provideUserPrincipal method
                    DeploymentOptions serverVerticleOptions =
                            readDeploymentOptions(ServerVerticle.class, workingDirPath);

                    try {
                        // just to be sure, try to fetch a new port to use
                        options.setServerPort(SystemHelper.getFreePort());
                    } catch (IOException e) { // NOPMD
                        // let's continue with the old port which should be still free (hopefully)
                    }

                    ServerVerticle dummyServerVerticle = createDummyServerVerticle(testInfo);
                    serverVerticleOptions.getConfig().put("port", options.getServerPort());
                    serverVerticleOptions.getConfig().put("authenticationChain", new JsonArray());
                    writeDeploymentOptions(ServerVerticle.class, serverVerticleOptions, workingDirPath);

                    undeployVerticles(ServerVerticle.class) // just to be sure, NO_WEB should not deploy a server
                            .compose(nothing -> deployVerticle(dummyServerVerticle, serverVerticleOptions))
                            .onComplete(result -> {
                                if (result.succeeded()) {
                                    isDummyServerVerticleDeployed = true;
                                }

                                testContext.succeedingThenComplete().handle(result.mapEmpty());
                                latch.countDown();
                            });
                } else {
                    testContext.completeNow();
                    latch.countDown();
                }
            }
        });

        if (!latch.await(DEFAULT_TIMEOUT_DURATION, DEFAULT_TIMEOUT_UNIT)) {
            throw new TimeoutException("Preparing NeonBee timed out");
        }
    }

    @AfterEach
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    void tearDown(Vertx vertx, VertxTestContext testContext) throws IOException {
        FileSystemHelper.deleteRecursive(vertx, workingDirPath).recover(throwable -> {
            if (throwable.getCause() instanceof DirectoryNotEmptyException) {
                // especially on windows machines, open file handles sometimes cause an issue that the directory cannot
                // be deleted, wait a little and try again afterwards
                return ConcurrentHelper.waitFor(vertx, 250)
                        .compose(nothing -> FileSystemHelper.deleteRecursive(vertx, workingDirPath));
            } else {
                return failedFuture(throwable);
            }
        }).onComplete(testContext.succeedingThenComplete());
    }

    /**
     * Override this method to influence the options to start {@link NeonBee} with. By default the (very restrictive)
     * options of {@link NeonBeeInstanceConfiguration} will be applied, which for instance does disable class path
     * scanning, watching files and job scheduling. It does however apply all profiles by default. Some examples how to
     * use this method:
     *
     * - In case your test is not requiring any HTTP connectivity via the {@link ServerVerticle}, add the
     * {@link NeonBeeProfile#NO_WEB} profile to improve performance of your tests:
     * {@code options.addActiveProfile(NO_WEB);}.
     *
     * - In case your test is requiring job scheduling / you want to test {@link JobVerticle}, use
     * {@link NeonBeeOptions.Mutable#setDisableJobScheduling(boolean)} and change the default to {@code true} instead.
     *
     * It is highly discouraged to change the server port, as a free random port is chosen by default. The working
     * directory is the only option that cannot be adapted by this method. Use {@link #provideWorkingDirectoryBuilder}
     * to adapt contents of the working directory instead.
     *
     * @param testInfo the test information of the currently executed test
     * @param options  the mutable options to adapt
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        // by default do not adapt any of the default options
    }

    /**
     * Override this method to provide a user principal which is added to <b>every</b> incoming HTTP request.
     *
     * @param testInfo the test information of the currently executed test
     * @return the user principal
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    protected JsonObject provideUserPrincipal(TestInfo testInfo) {
        return NO_USER_PRINCIPAL;
    }

    /**
     * Override this method to provide a non {@link WorkingDirectoryBuilder#standard() standard}
     * {@link WorkingDirectoryBuilder}.
     *
     * @param testInfo    The test information to be able to provide a test related WorkingDirectoryBuilder
     * @param testContext The Vert.x test context of the current test run.
     * @return the WorkingDirectoryBuilder
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        return WorkingDirectoryBuilder.standard();
    }

    /**
     * Convenience method to get the current NeonBee instance used in the test.
     *
     * @return The current NeonBee instance
     */
    public final NeonBee getNeonBee() {
        return neonBee;
    }

    /**
     * Checks for related {@link DeploymentOptions} and deploys the passed verticle instance.
     *
     * @param verticle The verticle instance to deploy
     * @return A succeeded future with the Deployment, or a failed future with the cause.
     */
    public Future<Deployment> deployVerticle(Verticle verticle) {
        return DeployableVerticle.fromVerticle(neonBee.getVertx(), verticle, null)
                .compose(deployable -> deployable.deploy(neonBee))
                .onSuccess(result -> LOGGER.info("Successfully deployed verticle {}", verticle))
                .onFailure(throwable -> LOGGER.error("Failed to deploy verticle {}", verticle, throwable));
    }

    /**
     * Deploys the passed verticle instance with the passed {@link DeploymentOptions}.
     *
     * @param verticle The verticle instance to deploy
     * @param options  The {@link DeploymentOptions} for the passed verticle
     * @return A succeeded future with the Deployment, or a failed future with the cause.
     */
    public Future<Deployment> deployVerticle(Verticle verticle, DeploymentOptions options) {
        return new DeployableVerticle(verticle, options).deploy(neonBee)
                .onSuccess(result -> LOGGER.info("Successfully deployed verticle {}", verticle))
                .onFailure(throwable -> LOGGER.error("Failed to deploy verticle {}", verticle, throwable));
    }

    /**
     * Checks for related {@link DeploymentOptions} and deploys the passed verticle class.
     *
     * @param verticleClass The verticle class to deploy
     * @return A succeeded future with the Deployment, or a failed future with the cause.
     */
    public Future<Deployment> deployVerticle(Class<? extends Verticle> verticleClass) {
        return DeployableVerticle.fromClass(neonBee.getVertx(), verticleClass, null)
                .compose(deployable -> deployable.deploy(neonBee))
                .onSuccess(
                        result -> LOGGER.info("Successfully deployed verticle with class {}", verticleClass.getName()))
                .onFailure(throwable -> LOGGER.error("Failed to deploy verticle with class {}", verticleClass.getName(),
                        throwable));
    }

    /**
     * Deploys the passed verticle class with the passed {@link DeploymentOptions}.
     *
     * @param verticleClass The verticle class to deploy
     * @param options       The {@link DeploymentOptions} for the passed verticle class
     * @return A succeeded future with the Deployment, or a failed future with the cause.
     */
    public Future<Deployment> deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
        return new DeployableVerticle(verticleClass, options).deploy(neonBee)
                .onSuccess(
                        result -> LOGGER.info("Successfully deployed verticle with class {}", verticleClass.getName()))
                .onFailure(throwable -> LOGGER.error("Failed to deploy verticle with class {}", verticleClass.getName(),
                        throwable));
    }

    /**
     * Undeploy the verticle assigned to the passed deploymentID.
     *
     * @param deploymentId The deploymentID to undeploy
     * @return A succeeded future, or a failed future with the cause.
     */
    public Future<Void> undeployVerticle(String deploymentId) {
        return DeploymentHelper.undeployVerticle(neonBee.getVertx(), deploymentId)
                .onSuccess(
                        result -> LOGGER.info("Successfully undeployed verticle with deployment ID {}", deploymentId))
                .onFailure(throwable -> LOGGER.error("Failed to undeployed verticle with deployment ID {}",
                        deploymentId, throwable));
    }

    /**
     * Undeploy all verticle of the passed class
     *
     * @param verticleClass The class of the verticle which should become undeployed.
     * @return A succeeded future, or a failed future with the cause.
     */
    public Future<Void> undeployVerticles(Class<? extends Verticle> verticleClass) {
        return DeploymentHelper.undeployAllVerticlesOfClass(neonBee.getVertx(), verticleClass).onSuccess(
                result -> LOGGER.info("Successfully undeployed verticle(s) with class {}", verticleClass.getName()))
                .onFailure(throwable -> LOGGER.error("Failed to undeployed verticle(s) with class {}",
                        verticleClass.getName(), throwable));
    }

    /**
     * Get the server config, with the actual port of the server (if specified differently via options).
     *
     * @param vertx the Vert.x instance that is associated to a NeonBee to retrieve the server port for
     * @return a future to the server configuration
     */
    public static Future<ServerConfig> readServerConfig(Vertx vertx) {
        return readServerConfig(requireNonNull(NeonBee.get(vertx),
                "Cannot resolve the server port as the provided Vert.x instance is not associated to a NeonBee instance"));
    }

    /**
     * Get the server config, with the actual port of the server (if specified differently via options).
     *
     * @param neonBee the NeonBee instance to get the server port for
     * @return a future to the server configuration
     */
    public static Future<ServerConfig> readServerConfig(NeonBee neonBee) {
        return readConfig(neonBee.getVertx(), ServerVerticle.class.getName()).map(DeploymentOptions::new)
                .map(DeploymentOptions::getConfig).map(ServerConfig::new)
                .onSuccess(config -> overridePort(neonBee, config));
    }

    private static ServerConfig readServerConfig(NeonBee neonBee, Path workingDirPath) {
        ServerConfig config = new ServerConfig(readDeploymentOptions(ServerVerticle.class, workingDirPath).getConfig());
        overridePort(neonBee, config);
        return config;
    }

    private static void overridePort(NeonBee neonBee, ServerConfig config) {
        Optional.ofNullable(neonBee.getOptions()).map(NeonBeeOptions::getServerPort).ifPresent(port -> {
            config.setPort(port);
        });
    }

    /**
     * Returns a pre-configured HTTP request which points to the NeonBee HTTP interface.
     *
     * <pre>
     * path: /raw/Hodor -&gt; result: &lt;host&gt;:&lt;port&gt;/raw/Hodor
     * </pre>
     *
     * @param method The HTTP method of the request
     * @param path   The path of the request
     * @return a pre-configured HTTP request which points to the NeonBee HTTP interface.
     */
    public HttpRequest<Buffer> createRequest(HttpMethod method, String path) {
        WebClientOptions opts = new WebClientOptions().setDefaultHost("localhost")
                .setDefaultPort(readServerConfig(neonBee, workingDirPath).getPort());
        HttpRequest<Buffer> request = WebClient.create(neonBee.getVertx(), opts).request(method, path);
        return isDummyServerVerticleDeployed ? request.bearerTokenAuthentication("dummy") : request;
    }

    /**
     * Creates a factory which can be used to construct a dummy {@link DataVerticle}.
     *
     * @param fqn the full qualified name of the {@link DataVerticle}
     * @return a {@link DummyDataVerticleFactory} which provides methods to construct a {@link DataVerticle}.
     */
    public DummyDataVerticleFactory createDummyDataVerticle(String fqn) {
        return DummyVerticleHelper.createDummyDataVerticle(fqn);
    }

    /**
     * Creates a factory which can be used to construct a dummy {@link EntityVerticle}.
     *
     * @param fqn the full qualified name of the {@link EntityVerticle}
     * @return a {@link DummyEntityVerticleFactory} which provides methods to construct an {@link EntityVerticle}.
     */
    public DummyEntityVerticleFactory createDummyEntityVerticle(FullQualifiedName fqn) {
        return DummyVerticleHelper.createDummyEntityVerticle(fqn);
    }

    private ServerVerticle createDummyServerVerticle(TestInfo testInfo) {
        return new ServerVerticle() {
            @Override
            protected Optional<AuthenticationHandler> createAuthChainHandler(List<AuthHandlerConfig> authChainConfig) {
                return Optional.of(new AuthenticationHandler() {
                    @Override
                    public void handle(RoutingContext ctx) {
                        ctx.setUser(User.create(provideUserPrincipal(testInfo)));
                        Session session = ctx.session();
                        if (session != null) {
                            // the user has upgraded from unauthenticated to authenticated
                            // session should be upgraded as recommended by owasp
                            session.regenerateId();
                        }

                        ctx.next();
                    }
                });
            }
        };
    }
}
