package io.neonbee;

import static ch.qos.logback.classic.ClassicConstants.CONFIG_FILE_PROPERTY;
import static io.neonbee.test.helper.OptionsHelper.options;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static java.lang.System.setProperty;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.LifecycleService;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.neonbee.NeonBeeOptions.Mutable;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;

@SuppressWarnings({ "rawtypes", "PMD.GodClass" })
public class NeonBeeExtension implements ParameterResolver, BeforeTestExecutionCallback, AfterTestExecutionCallback,
        BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback {

    /**
     * The {@link TestBase} is a little syntax sugar for test classes, that are dealing with clustered tests. This class
     * essentially wraps two things together and thusly decreases the risk of making a mistake:
     * <ol>
     * <li>Cluster tests should always run isolated. Thus the {@link TestBase} is {@link Isolated}.</li>
     * <li>For clustered tests it is recommended to use the {@link NeonBeeExtension} in order to initialize NeonBee
     * instances in the cluster. This test base implements the extension out of the box.</li>
     * </ol>
     */
    @Isolated("Clustered NeonBee tests must always run isolated. The default FakeClusterManager has a static state and multiple real cluster manager should never run side-by-side for multiple tests to avoid interfering")
    @ExtendWith(NeonBeeExtension.class)
    public static class TestBase {
        // nothing to add here, everything else is to be configured via the NeonBeeExtension
    }

    public static class EncryptedEventbusTestBase extends NeonBeeExtension.TestBase {
        public static final String KEYSTORE_PASSWORD = "wibble";

        public static final Path TRUSTSTORE_PATH = TEST_RESOURCES.resolveRelated("certificates/truststore.p12");

        public static final Path KEYSTORE_1_PATH = TEST_RESOURCES.resolveRelated("certificates/keystore-1.p12");

        public static final Path KEYSTORE_2_PATH = TEST_RESOURCES.resolveRelated("certificates/keystore-2.p12");

        public static final Path KEYSTORE_3_PATH = TEST_RESOURCES.resolveRelated("certificates/keystore-3.p12");

        /**
         * This method is called during parameter resolution and allows to configure a custom key- or truststore for
         * every NeonBee instance.
         *
         * @param options          the NeonBee options to configure trust options
         * @param parameterContext the related parameter context
         * @param extensionContext the related extension context
         */
        @SuppressWarnings("unused")
        protected void configureTrust(Mutable options, ParameterContext parameterContext,
                ExtensionContext extensionContext) {
            options.setClusterTruststorePassword(KEYSTORE_PASSWORD);
            options.setClusterTruststore(TRUSTSTORE_PATH);

            options.setClusterKeystorePassword(KEYSTORE_PASSWORD);
            switch (parameterContext.getIndex()) {
            case 0:
                options.setClusterKeystore(KEYSTORE_1_PATH);
                break;
            case 1:
                options.setClusterKeystore(KEYSTORE_2_PATH);
                break;
            case 2:
                options.setClusterKeystore(KEYSTORE_3_PATH);
                break;
            default:
                String msg = "Default implementation of configureTrust() only supports 3 different NeonBee nodes.";
                throw new ParameterResolutionException(msg);
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(NeonBeeExtension.class);

    public static final int DEFAULT_TIMEOUT_DURATION = 60;

    public static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;

    private static class ContextList extends ArrayList<VertxTestContext> {

        private static final long serialVersionUID = 6455420559550705670L;
        /*
         * There may be concurrent test contexts to join at a point of time because it is allowed to have several
         * user-defined lifecycle event handles (e.g., @BeforeEach, etc).
         */
    }

    private static final Set<Class> INJECTABLE_TYPES = Set.of(VertxTestContext.class, NeonBee.class);

    private static final String TEST_CONTEXT_KEY = "NeonBeeTestContext";

    static {
        setProperty(CONFIG_FILE_PROPERTY, Path.of("working_dir/config/logback.xml").toString());
        setProperty("hazelcast.logging.type", "slf4j");
        setProperty("org.jboss.logging.provider", "slf4j");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return INJECTABLE_TYPES.contains(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (type == NeonBee.class) {
            Mutable options;
            Optional<NeonBeeInstanceConfiguration> neonBeeInstanceConfiguration;

            try {
                neonBeeInstanceConfiguration = parameterContext.findAnnotation(NeonBeeInstanceConfiguration.class);
                options = options(neonBeeInstanceConfiguration);
            } catch (RuntimeException e) {
                throw new ParameterResolutionException("Error while finding a free port for server verticle.", e);
            }

            if (neonBeeInstanceConfiguration.map(NeonBeeInstanceConfiguration::encrypted).orElse(false)) {
                if (extensionContext.getRequiredTestInstance() instanceof EncryptedEventbusTestBase) {
                    ((EncryptedEventbusTestBase) extensionContext.getRequiredTestInstance())
                            .configureTrust(options, parameterContext, extensionContext);
                } else {
                    throw new ParameterResolutionException(
                            "Tests with encrypted eventbus must extend EncryptedEventbusTestBase");
                }
            }

            return unpack(store(extensionContext).getOrComputeIfAbsent(options.getInstanceName(),
                    key -> new ScopedObject<>(
                            createNeonBee(options,
                                    neonBeeInstanceConfiguration.map(NeonBeeInstanceConfiguration::clusterManager)
                                            .orElse(NeonBeeInstanceConfiguration.ClusterManager.FAKE)),
                            closeNeonBee())));
        }
        if (type == VertxTestContext.class) {
            return newTestContext(extensionContext);
        }
        throw new IllegalStateException("Looks like the ParameterResolver needs a fix...");
    }

    private Object unpack(Object object) {
        if (object instanceof Supplier) {
            return ((Supplier) object).get();
        }
        return object;
    }

    private VertxTestContext newTestContext(ExtensionContext extensionContext) {
        Store store = store(extensionContext);
        ContextList contexts = (ContextList) store.getOrComputeIfAbsent(TEST_CONTEXT_KEY, key -> new ContextList());
        VertxTestContext newTestContext = new VertxTestContext();
        contexts.add(newTestContext);
        return newTestContext;
    }

    private Store store(ExtensionContext extensionContext) {
        return extensionContext.getStore(Namespace.create(NeonBeeExtension.class, extensionContext));
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Not much we can do here ATM
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // We may wait on test contexts from @AfterAll methods
        joinActiveTestContexts(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        FakeClusterManager.reset(); // better safe than sorry
        // We may wait on test contexts from @BeforeAll methods
        joinActiveTestContexts(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        FakeClusterManager.reset(); // better safe than sorry
        // We may wait on test contexts from @AfterEach methods
        joinActiveTestContexts(context);
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        // We may wait on test contexts from @BeforeEach methods
        joinActiveTestContexts(context);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        // We may wait on the test context from a test
        joinActiveTestContexts(context);
    }

    private void joinActiveTestContexts(ExtensionContext extensionContext) throws Exception {
        if (extensionContext.getExecutionException().isPresent()) {
            return;
        }

        ContextList currentContexts = store(extensionContext).remove(TEST_CONTEXT_KEY, ContextList.class);
        if (currentContexts != null) {
            for (VertxTestContext context : currentContexts) {
                int timeoutDuration = DEFAULT_TIMEOUT_DURATION;
                TimeUnit timeoutUnit = DEFAULT_TIMEOUT_UNIT;
                Optional<Method> testMethod = extensionContext.getTestMethod();
                if (testMethod.isPresent() && testMethod.get().isAnnotationPresent(Timeout.class)) {
                    Timeout annotation = extensionContext.getRequiredTestMethod().getAnnotation(Timeout.class);
                    timeoutDuration = annotation.value();
                    timeoutUnit = annotation.timeUnit();
                } else if (extensionContext.getRequiredTestClass().isAnnotationPresent(Timeout.class)) {
                    Timeout annotation = extensionContext.getRequiredTestClass().getAnnotation(Timeout.class);
                    timeoutDuration = annotation.value();
                    timeoutUnit = annotation.timeUnit();
                }
                if (context.awaitCompletion(timeoutDuration, timeoutUnit)) {
                    if (context.failed()) {
                        Throwable throwable = context.causeOfFailure();
                        if (throwable instanceof Exception) {
                            throw (Exception) throwable;
                        } else {
                            throw new AssertionError(throwable);
                        }
                    }
                } else {
                    throw new TimeoutException("The test execution timed out. Make sure your asynchronous code "
                            + "includes calls to either VertxTestContext#completeNow(), VertxTestContext#failNow() "
                            + "or Checkpoint#flag()");
                }
            }
        }

        if (extensionContext.getParent().isPresent()) {
            joinActiveTestContexts(extensionContext.getParent().get());
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T obj) throws Exception;
    }

    /**
     *
     * Encapsulation of the supplier(creator) and consumer(cleaner) of an object.
     *
     */
    private static class ScopedObject<T> implements Supplier<T>, ExtensionContext.Store.CloseableResource {
        private final T object;

        private final ThrowingConsumer<T> cleaner;

        ScopedObject(T object, ThrowingConsumer<T> cleaner) {
            this.object = object;
            this.cleaner = cleaner;
        }

        @Override
        public void close() throws Throwable {
            cleaner.accept(object);
        }

        @Override
        public T get() {
            return object;
        }
    }

    private NeonBee createNeonBee(NeonBeeOptions options, NeonBeeInstanceConfiguration.ClusterManager clusterManager) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NeonBee> neonBeeBox = new AtomicReference<>();
        AtomicReference<Throwable> errorBox = new AtomicReference<>();

        CompositeMeterRegistry meterRegistry = new CompositeMeterRegistry();
        NeonBee.create(
                (NeonBee.OwnVertxFactory) (vertxOptions, clusterManagerInstance) -> NeonBee.newVertx(vertxOptions,
                        clusterManagerInstance, options, meterRegistry),
                clusterManager.factory(), options, null, meterRegistry).onComplete(ar -> {
                    if (ar.succeeded()) {
                        neonBeeBox.set(ar.result());
                    } else {
                        errorBox.set(ar.cause());
                    }

                    latch.countDown();
                });

        try {
            if (!latch.await(DEFAULT_TIMEOUT_DURATION, DEFAULT_TIMEOUT_UNIT)) {
                throw new VertxException(new TimeoutException("Failed to initialize NeonBee in time"));
            }
        } catch (InterruptedException e) {
            throw new VertxException("Got interrupted when initializing NeonBee", e);
        }

        Throwable throwable = errorBox.get();
        if (throwable != null) {
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            } else {
                throw new VertxException("Could not create NeonBee", throwable);
            }
        }

        return neonBeeBox.get();
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private ThrowingConsumer<NeonBee> closeNeonBee() {
        return neonBee -> {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorBox = new AtomicReference<>();

            // additional logic for tests, due to Hazelcast / Infinispan clusters tend to get stuck, after test
            // execution finishes, thus we forcefully will terminate the clusters at some point in time
            Vertx vertx = neonBee.getVertx();
            ClusterManager clusterManager = vertx instanceof VertxImpl ? ((VertxImpl) vertx).clusterManager() : null;
            if (clusterManager != null) {
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "neonbee-cluster-terminator");
                    thread.setDaemon(true);
                    return thread;
                }).schedule(() -> {
                    if (clusterManager instanceof HazelcastClusterManager) {
                        LifecycleService clusterLifecycleService =
                                ((HazelcastClusterManager) clusterManager).getHazelcastInstance().getLifecycleService();

                        if (clusterLifecycleService.isRunning()) {
                            LOGGER.warn("Forcefully terminating Hazelcast cluster after test");
                        }

                        // terminate the cluster in any case, if already terminated, this call will do nothing
                        clusterLifecycleService.terminate();
                    } else if (clusterManager instanceof InfinispanClusterManager) {
                        BasicCacheContainer cacheContainer =
                                ((InfinispanClusterManager) clusterManager).getCacheContainer();

                        if (cacheContainer instanceof EmbeddedCacheManager) {
                            if (!ComponentStatus.TERMINATED
                                    .equals(((EmbeddedCacheManager) cacheContainer).getStatus())) {
                                LOGGER.warn("Forcefully terminating Infinispan cache manager after test");
                            }

                            // terminate the cluster in any case, if already terminated, this call will do nothing
                            try {
                                ((Closeable) cacheContainer).close();
                            } catch (IOException e) {
                                LOGGER.warn("Failed to close Infinispan cluster manager after test", e);
                            }
                        } else if (cacheContainer != null) {
                            LOGGER.warn("Unknown Infinispan cache container type {}, cannot check for termination",
                                    cacheContainer.getClass());
                        }
                    }
                    // do not reset the FakeClusterManager here, as 10 seconds after test, another test could already be
                    // using another instance of the FakeClusterManager, which accesses the same static variables
                }, 10, TimeUnit.SECONDS);
            }

            neonBee.getVertx().close().onComplete(result -> {
                if (result.failed()) {
                    errorBox.set(result.cause());
                }

                // if we run with a FakeClusterManager we need to reset it after the test
                if (clusterManager instanceof FakeClusterManager) {
                    FakeClusterManager.reset();
                }

                latch.countDown();
            });

            if (!latch.await(DEFAULT_TIMEOUT_DURATION, DEFAULT_TIMEOUT_UNIT)) {
                throw new TimeoutException("Closing the Vertx context timed out");
            }

            Throwable throwable = errorBox.get();
            if (throwable != null) {
                if (throwable instanceof Exception) {
                    throw (Exception) throwable;
                } else {
                    throw new VertxException(throwable);
                }
            }
        };
    }
}
