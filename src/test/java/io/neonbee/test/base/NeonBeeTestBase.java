package io.neonbee.test.base;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.io.Resources;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.config.AuthHandlerConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.data.DataVerticle;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.deploy.Deployable;
import io.neonbee.internal.deploy.Deployment;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.helper.DeploymentHelper;
import io.neonbee.test.helper.DummyVerticleHelper;
import io.neonbee.test.helper.DummyVerticleHelper.DummyDataVerticleFactory;
import io.neonbee.test.helper.DummyVerticleHelper.DummyEntityVerticleFactory;
import io.neonbee.test.helper.FileSystemHelper;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
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
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class NeonBeeTestBase {

    private Path workingDirPath;

    private NeonBee neonbee;

    private boolean isDummyServerVerticleDeployed;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp(TestInfo testInfo, Vertx vertx, VertxTestContext testContext) throws Exception {
        // Build working directory
        workingDirPath = FileSystemHelper.createTempDirectory();
        provideWorkingDirectoryBuilder(testInfo, testContext).build(workingDirPath);
        NeonBeeOptions opts = new NeonBeeOptions.Mutable().setWorkingDirectory(workingDirPath).setIgnoreClassPath(true);

        URL defaulLogbackConfig = Resources.getResource(NeonBeeTestBase.class, "NeonBeeTestBase-Logback.xml");
        try (InputStream is = Resources.asByteSource(defaulLogbackConfig).openStream()) {
            Files.copy(is, opts.getConfigDirectory().resolve("logback.xml"));
        }

        // make required NeonBee method accessible, because TestBase is not in same package
        Method m = NeonBee.class.getDeclaredMethod("create", Supplier.class, NeonBeeOptions.class);
        m.setAccessible(true);
        Future<NeonBee> future =
                (Future<NeonBee>) m.invoke(null, (Supplier<Future<Vertx>>) () -> succeededFuture(vertx), opts);

        // For some reason the BeforeEach method in the subclass is called before testContext of this class
        // is completed. Therefore this CountDownLatch is needed.
        CountDownLatch latch = new CountDownLatch(1);
        future.onComplete(asyncNeonBee -> {
            if (asyncNeonBee.failed()) {
                testContext.failNow(asyncNeonBee.cause());
                latch.countDown();
            } else {
                neonbee = asyncNeonBee.result();

                DeploymentOptions serverVerticleOpts =
                        WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, workingDirPath);

                Optional.ofNullable(provideUserPrincipal(testInfo)).map(userPrincipal -> {
                    // Replace current ServerVerticle with a dummy ServerVerticle that also has a dummy AuthHandler to
                    // provide the user principal specified in the provideUserPrincipal method
                    ServerVerticle dummyServerVertice = createDummyServerVerticle(testInfo);

                    serverVerticleOpts.getConfig().put("authenticationChain", new JsonArray());

                    isDummyServerVerticleDeployed = true;
                    return undeployVerticles(ServerVerticle.class)
                            .compose(v -> deployVerticle(dummyServerVertice, serverVerticleOpts));
                }).orElse(succeededFuture()).onComplete(testContext.succeeding(v -> {
                    latch.countDown();
                    testContext.completeNow();
                }));
            }
        });

        latch.await();
    }

    @AfterEach
    void afterEach(Vertx vertx, VertxTestContext testContext) throws IOException {
        FileSystemHelper.deleteRecursive(vertx, workingDirPath).recover(throwable -> {
            if (throwable.getCause() instanceof DirectoryNotEmptyException) {
                // especially on windows machines, open file handles sometimes cause an issue that the directory cannot
                // be deleted, wait a little and try again afterwards
                return Future.future(handler -> vertx.setTimer(250, along -> handler.complete()))
                        .compose(nothing -> FileSystemHelper.deleteRecursive(vertx, workingDirPath));
            } else {
                return failedFuture(throwable);
            }
        }).onComplete(testContext.succeedingThenComplete());
    }

    /**
     * Override this method to provide a user principal which is added to <b>every</b> incoming HTTP request.
     *
     * @param testInfo The test information necessary to provide a test related user principal
     * @return the user principal
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    protected JsonObject provideUserPrincipal(TestInfo testInfo) {
        return null;
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
        return neonbee;
    }

    /**
     * Checks for related {@link DeploymentOptions} and deploys the passed verticle instance.
     *
     * @param verticle The verticle instance to deploy
     * @return A succeeded future with the Deployment, or a failed future with the cause.
     */
    public Future<Deployment> deployVerticle(Verticle verticle) {
        return Deployable.fromVerticle(neonbee.getVertx(), verticle, "", null)
                .compose(deployable -> deployable.deploy(neonbee.getVertx(), "").future());
    }

    /**
     * Deploys the passed verticle instance with the passed {@link DeploymentOptions}.
     *
     * @param verticle The verticle instance to deploy
     * @param options  The {@link DeploymentOptions} for the passed verticle
     * @return A succeeded future with the Deployment, or a failed future with the cause.
     */
    public Future<Deployment> deployVerticle(Verticle verticle, DeploymentOptions options) {
        return new Deployable(verticle, options).deploy(getNeonBee().getVertx(), "").future();
    }

    /**
     * Checks for related {@link DeploymentOptions} and deploys the passed verticle class.
     *
     * @param verticleClass The verticle class to deploy
     * @return A succeeded future with the Deployment, or a failed future with the cause.
     */
    public Future<Deployment> deployVerticle(Class<? extends Verticle> verticleClass) {
        return Deployable.fromClass(neonbee.getVertx(), verticleClass, "", null)
                .compose(deployable -> deployable.deploy(neonbee.getVertx(), "").future());
    }

    /**
     * Deploys the passed verticle class with the passed {@link DeploymentOptions}.
     *
     * @param verticleClass The verticle class to deploy
     * @param options       The {@link DeploymentOptions} for the passed verticle class
     * @return A succeeded future with the Deployment, or a failed future with the cause.
     */
    public Future<Deployment> deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
        return new Deployable(verticleClass, options).deploy(getNeonBee().getVertx(), "").future();
    }

    /**
     * Undeploys the verticle assigned to the passed deploymentID.
     *
     * @param deploymentID The deploymentID to undeploy
     * @return A succeeded future, or a failed future with the cause.
     */
    public Future<Void> undeployVerticle(String deploymentID) {
        return DeploymentHelper.undeployVerticle(neonbee.getVertx(), deploymentID);
    }

    /**
     * Undeploys all verticle of the passed class
     *
     * @param verticleClass The class of the verticle which should become undeployed.
     * @return A succeeded future, or a failed future with the cause.
     */
    public Future<Void> undeployVerticles(Class<? extends Verticle> verticleClass) {
        return DeploymentHelper.undeployAllVerticlesOfClass(neonbee.getVertx(), verticleClass);
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
        ServerConfig serverConfig = new ServerConfig(
                WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, workingDirPath).getConfig());
        int port = serverConfig.getPort();

        WebClientOptions opts = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(port);
        HttpRequest<Buffer> request = WebClient.create(getNeonBee().getVertx(), opts).request(method, path);
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
