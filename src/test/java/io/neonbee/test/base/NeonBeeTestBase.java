package io.neonbee.test.base;

import static io.neonbee.internal.Helper.readConfigBlocking;
import static io.neonbee.internal.verticle.ServerVerticle.CONFIG_PROPERTY_PORT_KEY;
import static io.vertx.core.Future.succeededFuture;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class NeonBeeTestBase {
    private Path workingDirPath;

    private NeonBee neonbee;

    private boolean isDummyServerVerticleDeployed;

    @BeforeEach
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
        Promise<NeonBee> startPromise = Promise.promise();
        Method m = NeonBee.class.getDeclaredMethod("instance", Supplier.class, NeonBeeOptions.class, Handler.class);
        m.setAccessible(true);
        m.invoke(null, (Supplier<Future<Vertx>>) () -> succeededFuture(vertx), opts, startPromise);

        // For some reason the BeforeEach method in the subclass is called before testContext of this class
        // is completed. Therefore this CountDownLatch is needed.
        CountDownLatch latch = new CountDownLatch(1);
        startPromise.future().onComplete(asyncNeonBee -> {
            if (asyncNeonBee.failed()) {
                testContext.failNow(asyncNeonBee.cause());
                latch.countDown();
            } else {
                neonbee = asyncNeonBee.result();
                Optional.ofNullable(provideUserPrincipal(testInfo)).map(userPrincipal -> {
                    // Replace current SevrerVerticle with a dummy SevrerVerticle that also has a dummy AuthHandler to
                    // provide the user principal specified in the provideUserPrincipal method
                    ServerVerticle dummyServerVertice = createDummyServerVerticle(testInfo);

                    DeploymentOptions serverVerticleOpts =
                            WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, workingDirPath);
                    serverVerticleOpts.getConfig().put("authenticationChain", new JsonArray().add(new JsonObject()));

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
    void afterEach() throws IOException {
        FileSystemHelper.deleteRecursiveBlocking(workingDirPath);
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
     * @param testInfo The test information to be able to provide a test related WorkingDirectoryBuilder
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
     * path: /raw/Hodor -&gt; result: <host>:<port>/raw/Hodor
     * </pre>
     *
     * @param method The HTTP method of the request
     * @param path   The path of the request
     * @return a pre-configured HTTP request which points to the NeonBee HTTP interface.
     */
    public HttpRequest<Buffer> createRequest(HttpMethod method, String path) {
        DeploymentOptions deploymentOptions =
                new DeploymentOptions(readConfigBlocking(getNeonBee().getVertx(), ServerVerticle.class.getName()));
        int port = deploymentOptions.getConfig().getInteger(CONFIG_PROPERTY_PORT_KEY, -1);

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
            public AuthenticationHandler createAuthHandler(JsonObject config) {
                return JWTAuthHandler.create(new JWTAuth() {

                    @Override
                    public void authenticate(JsonObject credentials, Handler<AsyncResult<User>> resultHandler) {
                        resultHandler.handle(succeededFuture(User.create(provideUserPrincipal(testInfo))));
                    }

                    @Override
                    public String generateToken(JsonObject claims, JWTOptions options) {
                        return null;
                    }
                });
            }
        };
    }
}
