package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class ODataFilterEqNull extends ODataEndpointTestBase {
    private ODataRequest oDataRequest;

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TestService1EntityVerticle.getDeclaredEntityModel());
    }

    private static Map<String, String> filterOf(String value) {
        return Map.of("$filter", value);
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        deployVerticle(new TestEntityVerticle()).onComplete(testContext.succeedingThenComplete());
        oDataRequest = new ODataRequest(TestEntityVerticle.TEST_ENTITY_SET_FQN);
    }

    @Test
    @DisplayName("Test that filter eq null is handled correctly")
    void testFilterEqNull(VertxTestContext testContext) {
        // we query the PropertyString100 property, but that is not added in the
        // ODataErrorHandlerTest.TestService1EntityVerticle.retrieveData method. That will cause a NoSuchFileException.
        Map<String, String> filter = filterOf("PropertyString eq null");
        oDataRequest.setQuery(filter);
        requestOData(oDataRequest)
                .onFailure(testContext::failNow)
                .onSuccess(event -> testContext.verify(() -> {
                    assertThat(event.statusCode()).isEqualTo(200);
                    JsonObject entity = event.bodyAsJsonObject().getJsonArray("value").getJsonObject(0);
                    assertThat(entity.getString("KeyPropertyString")).isEqualTo("id-0");
                    assertThat(entity.getString("PropertyString")).isNull();
                    testContext.completeNow();
                }));
    }

    @Test
    @DisplayName("Test that filter eq 'abc' is handled correctly")
    void tesFilterEqAbc(VertxTestContext testContext) {
        // we query the PropertyString100 property, but that is not added in the
        // ODataErrorHandlerTest.TestService1EntityVerticle.retrieveData method. That will cause a NoSuchFileException.
        Map<String, String> filter = filterOf("PropertyString eq 'abc'");
        oDataRequest.setQuery(filter);
        requestOData(oDataRequest)
                .onFailure(testContext::failNow)
                .onSuccess(event -> testContext.verify(() -> {
                    assertThat(event.statusCode()).isEqualTo(200);
                    testContext.completeNow();
                }));
    }

    private static class TestEntityVerticle extends EntityVerticle {
        public static final FullQualifiedName TEST_ENTITY_SET_FQN =
                new FullQualifiedName("io.neonbee.test.TestService1", "AllPropertiesNullable");

        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return Future.succeededFuture(Set.of(TEST_ENTITY_SET_FQN));
        }

        @Override
        public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
            Entity entity1 = new Entity()
                    .addProperty(new Property(null, "KeyPropertyString", ValueType.PRIMITIVE, "id-0"))
                    .addProperty(new Property(null, "PropertyString", ValueType.PRIMITIVE, null));

            return Future.succeededFuture(new EntityWrapper(TEST_ENTITY_SET_FQN, List.of(entity1)));
        }
    }
}
