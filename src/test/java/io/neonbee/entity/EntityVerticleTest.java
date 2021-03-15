package io.neonbee.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.entity.EntityVerticle.CDS_NAMESPACE_GROUP;
import static io.neonbee.entity.EntityVerticle.CDS_SERVICE_NAME_GROUP;
import static io.neonbee.entity.EntityVerticle.ENTITY_SET_NAME_GROUP;
import static io.neonbee.entity.EntityVerticle.SERVICE_NAMESPACE_GROUP;
import static io.neonbee.entity.EntityVerticle.URI_PATH_PATTERN;
import static io.neonbee.entity.EntityVerticle.sharedEntityMapName;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.vertx.core.Future.future;
import static io.vertx.core.Future.succeededFuture;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.queryoption.SystemQueryOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataAction;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataVerticle;
import io.neonbee.internal.verticle.ConsolidationVerticle;
import io.neonbee.test.base.EntityVerticleTestBase;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class EntityVerticleTest extends EntityVerticleTestBase {
    private EntityVerticle entityVerticleImpl1;

    private EntityVerticle entityVerticleImpl2;

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TEST_RESOURCES.resolveRelated("TestService1.csn"));
    }

    @BeforeEach
    void deployEntityVerticles(VertxTestContext testContext) {
        entityVerticleImpl1 = new EntityVerticleImpl1();
        entityVerticleImpl2 = new EntityVerticleImpl2();
        CompositeFuture.all(deployVerticle(entityVerticleImpl1), deployVerticle(entityVerticleImpl2),
                deployVerticle(new EntityVerticleImpl3())).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check if entity types are registered in shared entity map")
    void registerEntityTypes(VertxTestContext testContext) {
        AsyncMap<String, Object> asyncSharedMap = getNeonBee().getAsyncMap();
        CompositeFuture.join(future(
                asyncGet -> asyncSharedMap.get(sharedEntityMapName(new FullQualifiedName("ERP.Customers")), asyncGet)),
                future(asyncGet -> asyncSharedMap.get(sharedEntityMapName(new FullQualifiedName("Sales.Orders")),
                        asyncGet)))
                .onComplete(asyncComposite -> {
                    CompositeFuture future = asyncComposite.result();
                    testContext.verify(() -> {
                        assertThat(future.<JsonArray>resultAt(0)).containsExactly(
                                entityVerticleImpl1.getQualifiedName(), entityVerticleImpl2.getQualifiedName());
                        assertThat(future.<JsonArray>resultAt(1))
                                .containsExactly(entityVerticleImpl1.getQualifiedName());
                        testContext.completeNow();
                    });
                });
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check if registered entity types are returned via verticlesForEntityType")
    void queryVerticlesForEntityType(Vertx vertx, VertxTestContext testContext) {
        CompositeFuture
                .join(EntityVerticle.getVerticlesForEntityType(vertx, new FullQualifiedName("ERP", "Customers")),
                        EntityVerticle.getVerticlesForEntityType(vertx, new FullQualifiedName("Sales.Orders")))
                .onComplete(asyncComposite -> {
                    CompositeFuture future = asyncComposite.result();
                    testContext.verify(() -> {
                        assertThat(future.<JsonArray>resultAt(0)).containsExactly(
                                entityVerticleImpl1.getQualifiedName(), entityVerticleImpl2.getQualifiedName());
                        assertThat(future.<JsonArray>resultAt(1))
                                .containsExactly(entityVerticleImpl1.getQualifiedName());
                        testContext.completeNow();
                    });
                });
    }

    @Test
    @DisplayName("test EntityVerticle URI_PATH regexp")
    void testEntityURIPathRegex() {
        Matcher matcher;

        assertThat((matcher = URI_PATH_PATTERN.matcher("my.very/own.Service/Entity")).find()).isTrue();
        assertThat(matcher.group()).isEqualTo("my.very/own.Service/Entity");
        assertThat(matcher.group(SERVICE_NAMESPACE_GROUP)).isEqualTo("my.very/own.Service");
        assertThat(matcher.group(CDS_NAMESPACE_GROUP)).isEqualTo("my.very/own");
        assertThat(matcher.group(CDS_SERVICE_NAME_GROUP)).isEqualTo("Service");
        assertThat(matcher.group(ENTITY_SET_NAME_GROUP)).isEqualTo("Entity");

        assertThat((matcher = URI_PATH_PATTERN.matcher("my.Service/Entity")).find()).isTrue();
        assertThat(matcher.group()).isEqualTo("my.Service/Entity");
        assertThat(matcher.group(SERVICE_NAMESPACE_GROUP)).isEqualTo("my.Service");
        assertThat(matcher.group(CDS_NAMESPACE_GROUP)).isEqualTo("my");
        assertThat(matcher.group(CDS_SERVICE_NAME_GROUP)).isEqualTo("Service");
        assertThat(matcher.group(ENTITY_SET_NAME_GROUP)).isEqualTo("Entity");

        assertThat((matcher = URI_PATH_PATTERN.matcher("Service/Entity")).find()).isTrue();
        assertThat(matcher.group()).isEqualTo("Service/Entity");
        assertThat(matcher.group(SERVICE_NAMESPACE_GROUP)).isEqualTo("Service");
        assertThat(matcher.group(CDS_NAMESPACE_GROUP)).isNull();
        assertThat(matcher.group(CDS_SERVICE_NAME_GROUP)).isEqualTo("Service");
        assertThat(matcher.group(ENTITY_SET_NAME_GROUP)).isEqualTo("Entity");

        assertThat((matcher = URI_PATH_PATTERN.matcher("Service/Entity(1)")).find()).isTrue();
        assertThat(matcher.group(ENTITY_SET_NAME_GROUP)).isEqualTo("Entity");

        assertThat((matcher = URI_PATH_PATTERN.matcher("Service/Entity/$count")).find()).isTrue();
        assertThat(matcher.group(ENTITY_SET_NAME_GROUP)).isEqualTo("Entity");
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Get URI info from query")
    void parseUriInfoTest(Vertx vertx, VertxTestContext testContext) {
        Checkpoint checkpoint = testContext.checkpoint(3);

        EntityVerticle
                .parseUriInfo(vertx,
                        new DataQuery().setUriPath("/io.neonbee.test1.TestService1/AllPropertiesNullable")
                                .addParameter("$format", "json"))
                .onComplete(testContext.succeeding(uriInfo -> testContext.verify(() -> {
                    assertThat(uriInfo).isNotNull();
                    assertThat(uriInfo.getSystemQueryOptions().stream()
                            .collect(Collectors.toMap(SystemQueryOption::getName, SystemQueryOption::getText))
                            .get("$format")).isEqualTo("json");
                    assertThat(uriInfo.getUriResourceParts().stream().map(UriResource::getSegmentValue)
                            .collect(Collectors.toList())).contains("AllPropertiesNullable");
                    checkpoint.flag();
                })));

        EntityVerticle
                .parseUriInfo(vertx,
                        new DataQuery().setUriPath("/io.neonbee.test1.TestService1/AllPropertiesNullable('123')"))
                .onComplete(testContext.succeeding(uriInfo -> testContext.verify(() -> {
                    assertThat(((UriResourceEntitySet) uriInfo.getUriResourceParts().get(0)).getKeyPredicates().get(0)
                            .getText()).isEqualTo("'123'");
                    checkpoint.flag();
                })));

        EntityVerticle
                .parseUriInfo(vertx,
                        new DataQuery(DataAction.READ, "/io.neonbee.test1.TestService1/AllPropertiesNullable",
                                "$orderby=KeyPropertyString&$filter=KeyPropertyString eq 'Test123'"))
                .onComplete(testContext.succeeding(uriInfo -> testContext.verify(() -> {
                    assertThat(uriInfo.getUriResourceParts().stream().map(UriResource::getSegmentValue)
                            .collect(Collectors.toList())).contains("AllPropertiesNullable");
                    checkpoint.flag();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("requestEntity must call ConsolidationVerticle if more then one EntityVerticle is registered for Entity")
    void requestEntityWithConsolidationVerticleTest(VertxTestContext testContext) {
        DataVerticle<EntityWrapper> dummy =
                createDummyDataVerticle(ConsolidationVerticle.QUALIFIED_NAME).withDynamicResponse((dq, dc) -> {
                    FullQualifiedName entityTypeName =
                            new FullQualifiedName(dq.getHeader(ConsolidationVerticle.ENTITY_TYPE_NAME_HEADER));
                    assertThat(entityTypeName).isEqualTo(EntityVerticleImpl1.FQN_ERP_CUSTOMERS);
                    testContext.completeNow();
                    return null;
                });

        undeployVerticles(ConsolidationVerticle.class).compose(v -> deployVerticle(dummy))
                .compose(v -> requestEntity(EntityVerticleImpl1.FQN_ERP_CUSTOMERS))
                .onComplete(testContext.succeeding(v -> {}));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("requestEntity must call ConsolidationVerticle if more then one EntityVerticle is registered for Entity")
    void requestEntityTest(VertxTestContext testContext) {
        requestEntity(EntityVerticleImpl3.FQN_TEST_PRODUCTS)
                .onComplete(testContext.succeeding(ew -> testContext.verify(() -> {
                    assertThat(ew.getEntities()).containsExactlyElementsIn(EntityVerticleImpl3.TEST_PRODUCTS);
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("EntityVerticles should announce their entities, as soon as they are deployed and if the models reload")
    void announceEntityVerticle(Vertx testVertx, VertxTestContext testContext) {
        Checkpoint checkpoint = testContext.checkpoint(2);
        EntityVerticle dummyEntityVerticle = new EntityVerticle() {

            @Override
            public Future<Set<FullQualifiedName>> entityTypeNames() {
                checkpoint.flag();
                return succeededFuture(null);
            }
        };

        deployVerticle(dummyEntityVerticle).onComplete(testContext.succeeding(nextHandler -> {
            testVertx.eventBus().publish(EntityModelManager.EVENT_BUS_MODELS_LOADED_ADDRESS, null);
        }));
    }
}

@SuppressWarnings("PMD.TestClassWithoutTestCases")
class EntityVerticleImpl1 extends EntityVerticle {
    static final FullQualifiedName FQN_ERP_CUSTOMERS = new FullQualifiedName("ERP", "Customers");

    static final FullQualifiedName FQN_SALES_ORDERS = new FullQualifiedName("Sales.Orders");

    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return succeededFuture(Set.of(FQN_ERP_CUSTOMERS, FQN_SALES_ORDERS));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
        return succeededFuture(new EntityWrapper(FQN_ERP_CUSTOMERS, (Entity) null));
    }
}

@NeonBeeDeployable(namespace = "test")
@SuppressWarnings("PMD.TestClassWithoutTestCases")
class EntityVerticleImpl2 extends EntityVerticle {

    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return succeededFuture(Set.of(EntityVerticleImpl1.FQN_ERP_CUSTOMERS));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
        return succeededFuture(new EntityWrapper(EntityVerticleImpl1.FQN_ERP_CUSTOMERS, (Entity) null));
    }
}

@NeonBeeDeployable(namespace = "test")
@SuppressWarnings("PMD.TestClassWithoutTestCases")
class EntityVerticleImpl3 extends EntityVerticle {

    static final FullQualifiedName FQN_TEST_PRODUCTS = new FullQualifiedName("TestService1.TestProducts");

    static final List<Entity> TEST_PRODUCTS = List.of(createTestProducts("LC", "Lord Citrange", "God"),
            createTestProducts("A207", "Apache 207", "Gangster der sein Tanzbein schwingt"));

    private static Entity createTestProducts(String id, String name, String description) {
        Entity testProduct = new Entity();
        testProduct.addProperty(new Property(null, "ID", ValueType.PRIMITIVE, id));
        testProduct.addProperty(new Property(null, "name", ValueType.PRIMITIVE, name));
        testProduct.addProperty(new Property(null, "description", ValueType.PRIMITIVE, description));
        return testProduct;
    }

    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return succeededFuture(Set.of(FQN_TEST_PRODUCTS));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
        return succeededFuture(new EntityWrapper(FQN_TEST_PRODUCTS, TEST_PRODUCTS));
    }
}
