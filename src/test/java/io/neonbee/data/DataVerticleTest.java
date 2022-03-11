package io.neonbee.data;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeProfile.NO_WEB;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.Boolean.parseBoolean;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.NeonBeeOptions;
import io.neonbee.config.MetricsConfig;
import io.neonbee.data.internal.metrics.ConfiguredDataVerticleMetrics;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
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

    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        options.addActiveProfile(NO_WEB);
    }

    @BeforeEach
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void deployEntityVerticles(VertxTestContext testContext) {
        Checkpoint checkpoint = testContext.checkpoint(3);
        deployVerticle(this.dataVerticleImpl0 = new DataVerticleImpl0())
                .onComplete(testContext.succeeding(result -> checkpoint.flag()));
        deployVerticle(this.dataVerticleImpl1 = new DataVerticleImpl1())
                .onComplete(testContext.succeeding(result -> checkpoint.flag()));
        deployVerticle(this.dataVerticleImpl2 = new DataVerticleImpl2())
                .onComplete(testContext.succeeding(result -> checkpoint.flag()));
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
    void testGetNamespace() {
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

        Checkpoint checkpoint = testContext.checkpoint(3);

        assertDataEquals(requestData(DataVerticleImpl0.NAME), DataVerticleImpl0.EXPECTED_RESPONSE, testContext)
                .onComplete(testContext.succeeding(nothing -> checkpoint.flag()));
        assertDataEquals(requestData(DataVerticleImpl1.NAME), DataVerticleImpl1.EXPECTED_RESPONSE, testContext)
                .onComplete(testContext.succeeding(nothing -> checkpoint.flag()));
        assertDataFailure(requestData(requestDVImpl2), new DataException(400, "Bad Request"), testContext)
                .onComplete(testContext.succeeding(nothing -> checkpoint.flag()));
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
        assertThat(DataVerticle.createQualifiedName("nameSpace", "verticle")).isEqualTo("namespace/verticle");
    }

    /**
     * Eunum for the testMetricsConfiguration test method.
     */
    private enum VerticleMetricsConfigTestValues {
        /**
         * Configuration with "enabled" = null
         */
        NULL(null),
        /**
         * Configuration with "enabled" = false
         */
        FALSE(false),
        /**
         * Configuration with "enabled" = true
         */
        TRUE(true),
        /**
         * Configuration is null
         */
        CONFIG_NULL(null);

        private final Boolean value;

        VerticleMetricsConfigTestValues(Boolean value) {
            this.value = value;
        }

        public JsonObject getConfig() {
            if (this == CONFIG_NULL) {
                return null;
            }
            return getConfig(value);
        }

        private JsonObject getConfig(Boolean verticleMetricsEnabled) {
            return new JsonObject().put(DataVerticle.CONFIG_METRICS_KEY,
                    new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, verticleMetricsEnabled));
        }
    }

    static Stream<Arguments> metricsConfigurationArguments() {
        return Stream.of(Arguments.of(false, VerticleMetricsConfigTestValues.NULL, false),
                Arguments.of(false, VerticleMetricsConfigTestValues.FALSE, false),
                Arguments.of(false, VerticleMetricsConfigTestValues.TRUE, false),
                Arguments.of(false, VerticleMetricsConfigTestValues.CONFIG_NULL, false),
                Arguments.of(true, VerticleMetricsConfigTestValues.FALSE, false),
                Arguments.of(true, VerticleMetricsConfigTestValues.NULL, true),
                Arguments.of(true, VerticleMetricsConfigTestValues.TRUE, true),
                Arguments.of(true, VerticleMetricsConfigTestValues.CONFIG_NULL, true),
                Arguments.of(null, VerticleMetricsConfigTestValues.TRUE, false),
                Arguments.of(null, VerticleMetricsConfigTestValues.FALSE, false),
                Arguments.of(null, VerticleMetricsConfigTestValues.NULL, false),
                Arguments.of(null, VerticleMetricsConfigTestValues.CONFIG_NULL, false));
    }

    @ParameterizedTest(
            name = "{index}: MetricsConfig enabled:{0}, verticle metric configuration enabled:{1}, expected:{2}")
    @MethodSource("metricsConfigurationArguments")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 1)
    @DisplayName("Tests the creation of the actual metric configuration.")
    @SuppressWarnings("PMD.UnusedFormalParameter")
    void testMetricsConfiguration(Boolean globalEnabled, VerticleMetricsConfigTestValues verticleMetricsConfigValue,
            boolean expectedValue) {
        MetricsConfig mc = getGlobalMetricsConfig(globalEnabled);
        JsonObject verticleMetricsConfig = verticleMetricsConfigValue.getConfig();

        JsonObject config = new DataVerticleMetricConfig(verticleMetricsConfig).getMetricsConfig(mc);
        assertThat(config).isEqualTo(new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, expectedValue));
    }

    private MetricsConfig getGlobalMetricsConfig(Boolean globalEnabled) {
        if (globalEnabled == null) {
            return null;
        }
        return new MetricsConfig().setEnabled(globalEnabled);
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
            if (parseBoolean(query.getParameter("ping"))) {
                return succeededFuture("Pong");
            }
            throw new DataException(400, "Bad Request");
        }
    }

    private static class DataVerticleMetricConfig extends DataVerticle<String> {

        private final JsonObject config;

        DataVerticleMetricConfig(JsonObject verticleMetricConfig) {
            super();
            config = verticleMetricConfig;
        }

        @Override
        public JsonObject config() {
            return config;
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException("test implementation");
        }

        @Override
        public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
            throw new UnsupportedOperationException("test implementation");
        }
    }
}
