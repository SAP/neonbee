package io.neonbee.health;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.health.DummyHealthCheck.DUMMY_ID;
import static io.neonbee.internal.verticle.HealthCheckVerticle.SHARED_MAP_KEY;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static io.neonbee.test.helper.ReflectionHelper.setValueOfPrivateField;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeMockHelper;
import io.neonbee.config.HealthConfig;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.health.internal.HealthCheck;
import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.internal.WriteSafeRegistry;
import io.neonbee.internal.codec.DataQueryMessageCodec;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.internal.verticle.HealthCheckVerticle;
import io.neonbee.test.helper.DeploymentHelper;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.healthchecks.Status;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HealthCheckRegistryTest {
    private static final long RETENTION_TIME = 12L;

    private NeonBee neonBee;

    @BeforeEach
    void setUp(Vertx vertx) {
        try {
            vertx.eventBus().registerDefaultCodec(DataQuery.class, new DataQueryMessageCodec());
        } catch (IllegalStateException ignored) {
            // fall through
        }

        neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx, defaultOptions().setClustered(false),
                new NeonBeeConfig().setHealthConfig(new HealthConfig().setEnabled(true).setTimeout(2)));
    }

    @Test
    @DisplayName("it can list all health checks")
    void getHealthChecks(Vertx vertx) {
        HealthCheckRegistry registry = new HealthCheckRegistry(vertx);

        assertThat(registry.getHealthChecks()).isEmpty();
        registry.checks.put("check-1", mock(HealthCheck.class));
        assertThat(registry.getHealthChecks().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("it can register global checks")
    void registerGlobalCheck() throws HealthCheckException {
        HealthCheck healthCheck = neonBee.getHealthCheckRegistry().registerGlobalCheck(DUMMY_ID, RETENTION_TIME,
                nb -> p -> p.complete(new Status()), new JsonObject());

        assertThat(healthCheck.getId()).contains(DUMMY_ID);
        assertThat(healthCheck.getRetentionTime()).isEqualTo(RETENTION_TIME);
        assertThat(neonBee.getHealthCheckRegistry().getHealthChecks().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("it can register node checks")
    void registerNodeCheck() throws HealthCheckException {
        HealthCheck healthCheck = neonBee.getHealthCheckRegistry().registerNodeCheck(DUMMY_ID, RETENTION_TIME,
                nb -> p -> p.complete(new Status()), new JsonObject());

        assertThat(healthCheck.getId()).matches(Pattern.compile("node." + neonBee.getNodeId() + "." + DUMMY_ID));
        assertThat(healthCheck.getRetentionTime()).isEqualTo(RETENTION_TIME);
        assertThat(neonBee.getHealthCheckRegistry().getHealthChecks().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("it can register HealthChecks via object")
    void register() {
        AbstractHealthCheck check = spy(new MemoryHealthCheck(neonBee));
        neonBee.getHealthCheckRegistry().register(check);

        verify(check).register(eq(neonBee.getHealthCheckRegistry()));
    }

    @Test
    @DisplayName("it can only register health checks with unique names")
    void registerHealthCheckOnlyOnce() throws HealthCheckException {
        neonBee.getHealthCheckRegistry().registerGlobalCheck(DUMMY_ID, RETENTION_TIME,
                nb -> p -> p.complete(new Status()), new JsonObject());

        HealthCheckException exception = assertThrows(HealthCheckException.class, () -> neonBee.getHealthCheckRegistry()
                .registerGlobalCheck(DUMMY_ID, RETENTION_TIME, nb -> p -> p.complete(new Status()), new JsonObject()));

        assertThat(exception.getMessage()).isEqualTo("HealthCheck '" + DUMMY_ID + "' already registered.");
        assertThat(neonBee.getHealthCheckRegistry().checks.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("should prefer disabling of health checks from health check config of config folder")
    void testCustomConfigEnabled(Vertx vertx) throws HealthCheckException {
        neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx, defaultOptions(),
                new NeonBeeConfig().setHealthConfig(new HealthConfig().setEnabled(true)));

        HealthCheck check = neonBee.getHealthCheckRegistry().registerGlobalCheck(DUMMY_ID, RETENTION_TIME,
                nb -> p -> p.complete(new Status()), new JsonObject().put("enabled", false));

        assertThat(check).isNull();
    }

    @Test
    @DisplayName("should prefer enablement of health checks and timeout from health check config of config folder")
    void testCustomConfigDisabled(Vertx vertx) throws HealthCheckException {
        neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx, defaultOptions(),
                new NeonBeeConfig().setHealthConfig(new HealthConfig().setEnabled(false).setTimeout(2)));
        neonBee.getHealthCheckRegistry().healthChecks = spy(neonBee.getHealthCheckRegistry().healthChecks);

        neonBee.getHealthCheckRegistry().registerGlobalCheck(DUMMY_ID, RETENTION_TIME,
                nb -> p -> p.complete(new Status()), new JsonObject().put("enabled", true).put("timeout", 3));

        verify(neonBee.getHealthCheckRegistry().healthChecks).register(eq(DUMMY_ID), eq(3000L), any());
        assertThat(neonBee.getHealthCheckRegistry().checks.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("it can unregister health checks by health object and id")
    void testUnregister(VertxTestContext testContext) {
        HealthCheckRegistry registry = neonBee.getHealthCheckRegistry();
        registry.healthChecks = spy(registry.healthChecks);
        AbstractHealthCheck check = new DummyHealthCheck(neonBee);

        registry.register(check).compose(v -> {
            testContext.verify(() -> {
                assertThat(registry.checks.size()).isEqualTo(1);
                registry.unregister(check);
                assertThat(registry.checks.size()).isEqualTo(0);
            });
            return registry.register(check);
        }).onComplete(testContext.succeeding(v -> {
            testContext.verify(() -> {
                assertThat(registry.checks.size()).isEqualTo(1);
                registry.unregister(check.getId());
                assertThat(registry.checks.size()).isEqualTo(0);

                verify(registry.healthChecks, times(2)).register(eq(check.getId()), eq(2000L), any());
                verify(registry.healthChecks, times(2)).unregister(eq(check.getId()));
            });
            testContext.completeNow();
        }));
    }

    @Test
    @DisplayName("it can request data from all health check verticles registered in shared map and consolidates the result")
    void testConsolidateHealthCheckResultsClustered(Vertx vertx, VertxTestContext testContext) throws Exception {
        neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx, defaultOptions().setClustered(true));

        Checkpoint requestReceivedVerticle1 = testContext.checkpoint();
        Checkpoint requestReceivedVerticle2 = testContext.checkpoint();
        Checkpoint receivedResultsValidated = testContext.checkpoint();

        HealthCheckVerticle healthCheckVerticle1 = new HealthCheckVerticle() {
            @Override
            public String getName() {
                return super.getName() + "-test1";
            }

            @Override
            public Future<JsonArray> retrieveData(DataQuery query, DataContext context) {
                requestReceivedVerticle1.flag();
                return super.retrieveData(query, context);
            }
        };
        HealthCheckVerticle healthCheckVerticle2 = new HealthCheckVerticle() {
            @Override
            public String getName() {
                return super.getName() + "-test2";
            }

            @Override
            public Future<JsonArray> retrieveData(DataQuery query, DataContext context) {
                requestReceivedVerticle2.flag();
                return super.retrieveData(query, context);
            }
        };

        AsyncMap<String, Object> sharedMap = new SharedDataAccessor(vertx, HealthCheckVerticle.class)
                .<String, Object>getAsyncMap("#sharedMap").result();
        setValueOfPrivateField(neonBee, "sharedAsyncMap", sharedMap);

        // undeploy the system deployed health check verticles of neonbee
        DeploymentHelper.undeployAllVerticlesOfClass(neonBee.getVertx(), HealthCheckVerticle.class)
                .compose(v -> AsyncHelper.allComposite(
                        List.of(vertx.deployVerticle(healthCheckVerticle1), vertx.deployVerticle(healthCheckVerticle2),
                                neonBee.getHealthCheckRegistry().register(new DummyHealthCheck(neonBee)))))
                .onSuccess(v -> {
                    neonBee.getHealthCheckRegistry().collectHealthCheckResults()
                            .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                                Function<String, Long> matchingNameCount = id -> result.getJsonArray("checks").stream()
                                        .filter(c -> ((JsonObject) c).getString("id").endsWith(id)).count();

                                assertThat(matchingNameCount.apply(DUMMY_ID)).isEqualTo(1);
                                // only one check each, because two health check verticles are deployed, but on the same
                                // node -> thus the check id is the same and does not get added twice to the result.

                                assertThat(result.getString("outcome")).isNotNull();
                                assertThat(result.getString("status")).isEqualTo(result.getString("outcome"));
                                receivedResultsValidated.flag();
                            })));
                }).onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("it requests data from local registry only if in non-clustered mode")
    void testConsolidateHealthCheckResultsNonClustered(Vertx vertx, VertxTestContext testContext) {
        Checkpoint cp = testContext.checkpoint(2);

        HealthCheckRegistry mock = new HealthCheckRegistry(vertx) {
            @Override
            Future<List<JsonObject>> getLocalHealthCheckResults() {
                cp.flag();
                return super.getLocalHealthCheckResults();
            }
        };

        mock.register(new DummyHealthCheck(neonBee)).compose(hc -> mock.collectHealthCheckResults())
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    Function<String, Long> matchingNameCount = id -> result.getJsonArray("checks").stream()
                            .filter(c -> ((JsonObject) c).getString("id").equals(id)).count();
                    assertThat(matchingNameCount.apply(DUMMY_ID)).isEqualTo(1);
                    assertThat(result.getString("outcome")).isEqualTo("UP");
                    assertThat(result.getString("status")).isEqualTo("UP");
                    cp.flag();
                })));
    }

    @Test
    @DisplayName("it requests data for a specific check")
    void testConsolidateResultsForSpecificCheck(Vertx vertx, VertxTestContext testContext) {
        String checkName = "dummy-a";
        HealthCheckRegistry registry = new HealthCheckRegistry(vertx);
        registry.register(new DummyHealthCheck(neonBee))
                .compose(hc -> registry.register(new DummyHealthCheck(neonBee) {
                    @Override
                    public String getId() {
                        return checkName;
                    }
                }))
                .compose(hc -> registry.register(new DummyHealthCheck(neonBee) {
                    @Override
                    public String getId() {
                        return "dummy-b";
                    }

                    @Override
                    public Function<NeonBee, Handler<Promise<Status>>> createProcedure() {
                        return nb -> promise -> promise.complete(new Status().setKO());
                    }

                    @Override
                    public boolean isGlobal() {
                        return false;
                    }
                }))
                .compose(hc -> registry.collectHealthCheckResults(new DataContextImpl(), checkName))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    Function<String, Long> matchingNameCount = id -> result.getJsonArray("checks").stream()
                            .filter(c -> ((JsonObject) c).getString("id").equals(id)).count();

                    assertThat(matchingNameCount.apply(checkName)).isEqualTo(1);
                    assertThat(result.getString("outcome")).isEqualTo("UP");
                    assertThat(result.getString("status")).isEqualTo("UP");
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("it does not fail if some verticle addresses are not reachable")
    void test(Vertx vertx, VertxTestContext testContext) {
        neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx, defaultOptions().setClustered(true));
        String verticleName = "not-existing-verticle-name";
        WriteSafeRegistry<String> registry = new WriteSafeRegistry<>(vertx, HealthCheckRegistry.REGISTRY_NAME);
        Future<Void> setupFuture = registry.register(SHARED_MAP_KEY, verticleName);

        setupFuture
                .compose(unused -> DeploymentHelper.undeployAllVerticlesOfClass(neonBee.getVertx(),
                        HealthCheckVerticle.class))
                .compose(v -> AsyncHelper.allComposite(List.of(vertx.deployVerticle(new HealthCheckVerticle()),
                        neonBee.getHealthCheckRegistry().register(new DummyHealthCheck(neonBee)))))
                .compose(v -> neonBee.getHealthCheckRegistry().collectHealthCheckResults())
                .onSuccess(result -> testContext.verify(() -> assertThat(result.getString("status")).isEqualTo("UP")))
                .compose(result -> registry.get(SHARED_MAP_KEY)).map(JsonArray.class::cast)
                .onSuccess(registeredVerticles -> testContext.verify(() -> {
                    assertThat(registeredVerticles).hasSize(2);
                    assertThat(registeredVerticles).contains(verticleName);
                    testContext.completeNow();
                })).onFailure(testContext::failNow);
    }
}
