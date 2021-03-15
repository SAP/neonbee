package io.neonbee.data;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.Future.succeededFuture;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class DataVerticleTest extends DataVerticleTestBase {
    // Data Verticle that has NOT the @NeonBeeDeployable annotation
    private DataVerticleImpl0 dataVerticleImpl0;

    // Data Verticle that has the @NeonBeeDeployable annotation but w/o a specified namespace
    private DataVerticleImpl1 dataVerticleImpl1;

    // Data Verticle that has the @NeonBeeDeployable(namespace = "DataVerticleTestNamespace2") annotation but w/ the
    // effective namespace dataverticletestnamespace2
    private DataVerticleImpl2 dataVerticleImpl2;

    @BeforeEach
    void deployEntityVerticles(VertxTestContext testContext) {
        this.dataVerticleImpl0 = new DataVerticleImpl0();
        this.dataVerticleImpl1 = new DataVerticleImpl1();
        this.dataVerticleImpl2 = new DataVerticleImpl2();
        CompositeFuture.all(deployVerticle(dataVerticleImpl0), deployVerticle(dataVerticleImpl1),
                deployVerticle(dataVerticleImpl2)).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Ensure that start() from AbstractVerticle is called")
    void ensureStartMethodIsCalled(VertxTestContext testContext) {
        DataVerticle<Void> verticle = new DataVerticle<Void>() {

            @Override
            public void start() throws Exception {
                testContext.completeNow();
            }

            @Override
            public String getName() {
                return "NAME";
            }
        };
        deployVerticle(verticle);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Get the correct namespace for verticle with and without the @NeonBeeDeployable annotation")
    void getNamespaceTest() {
        // No annotation available, therefore the namespace has to be null
        assertThat(dataVerticleImpl0.getNamespace()).isNull();

        // No namespace provided via annotation, therefore the namespace has to be null
        assertThat(dataVerticleImpl1.getNamespace()).isNull();

        // DataVerticleTestNamespace2 was provided and will be effectively be dataverticletestnamespace2
        assertThat(dataVerticleImpl2.getNamespace())
                .isEqualTo("DataVerticleTestNamespace2".toLowerCase(Locale.ENGLISH));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check if DataVerticle is registered with the correct qualifiedName (address)")
    void registerEventbusTest(VertxTestContext testContext) {
        String addressDVImpl2 = "DataVerticleTestNamespace2".toLowerCase(Locale.ENGLISH) + "/" + DataVerticleImpl2.NAME;
        DataRequest requestDVImpl2 = new DataRequest(addressDVImpl2, new DataQuery().addParameter("ping", ""));

        Future<String> dataVerticle0Response = requestData(DataVerticleImpl0.NAME);
        Future<String> dataVerticle1Response = requestData(DataVerticleImpl1.NAME);
        Future<String> dataVerticle2Response = requestData(requestDVImpl2);

        CompositeFuture
                .all(assertDataEquals(dataVerticle0Response, DataVerticleImpl0.EXPECTED_RESPONSE, testContext),
                        assertDataEquals(dataVerticle1Response, DataVerticleImpl1.EXPECTED_RESPONSE, testContext),
                        assertDataFailure(dataVerticle2Response, new DataException(400, "Bad Request"), testContext))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Check if DataVerticle registers itself as local consumer on deployment")
    void registerLocalConsumerTest() {
        assertThat(getNeonBee().isLocalConsumerAvailable(dataVerticleImpl0.getAddress())).isTrue();
        assertThat(getNeonBee().isLocalConsumerAvailable(dataVerticleImpl1.getAddress())).isTrue();
        assertThat(getNeonBee().isLocalConsumerAvailable(dataVerticleImpl2.getAddress())).isTrue();
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check if DataVerticle unregisters itself as local consumer on undeployment")
    void unregisterLocalConsumerTest(VertxTestContext testContext) {
        assertThat(getNeonBee().isLocalConsumerAvailable(dataVerticleImpl0.getAddress())).isTrue();
        undeployVerticles(DataVerticleImpl0.class).onComplete(testContext.succeeding(v -> testContext.verify(() -> {
            assertThat(getNeonBee().isLocalConsumerAvailable(dataVerticleImpl0.getAddress())).isFalse();
            testContext.completeNow();
        })));
    }

    @Test
    void createQualifiedName() {
        assertThat(DataVerticle.createQualifiedName("namespace", "verticle")).isEqualTo("namespace/verticle");
    }

    private static class DataVerticleImpl0 extends DataVerticle<String> {
        public static final String NAME = "ExpectedName0";

        public static final String EXPECTED_RESPONSE = "Hello, I am " + NAME;

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
            return succeededFuture(EXPECTED_RESPONSE);
        }
    }

    @NeonBeeDeployable
    private static class DataVerticleImpl1 extends DataVerticle<String> {
        public static final String NAME = "ExpectedName1";

        public static final String EXPECTED_RESPONSE = "Hello, I am " + NAME;

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
            return succeededFuture("Hello, I am " + NAME);
        }
    }

    @NeonBeeDeployable(namespace = "DataVerticleTestNamespace2")
    private static class DataVerticleImpl2 extends DataVerticle<String> {
        public static final String NAME = "ExpectedName2";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
            if ("true".equalsIgnoreCase(query.getParameter("ping"))) {
                return succeededFuture("Pong");
            }
            throw new DataException(400, "Bad Request");
        }
    }
}
