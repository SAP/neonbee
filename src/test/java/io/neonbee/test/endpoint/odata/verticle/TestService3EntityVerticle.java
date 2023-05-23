package io.neonbee.test.endpoint.odata.verticle;

import static io.neonbee.test.helper.EntityHelper.createEntity;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class TestService3EntityVerticle extends EntityVerticle {
    public static final FullQualifiedName TEST_ENTITY_SET_FQN =
            new FullQualifiedName("io.neonbee.test3.TestService3", "TestCars");

    public static final String ENTITY_URL = "/io.neonbee.test3.TestService3/TestCars(/0)";

    public static final JsonObject ENTITY_DATA_1 =
            new JsonObject().put("ID", 0).put("name", "Car 0").put("description", "This is Car 0");

    public static final JsonObject ENTITY_DATA_2 =
            new JsonObject().put("ID", 1).put("name", "Car 1").put("description", "This is Car 1");

    public static final JsonObject ENTITY_DATA_3 =
            new JsonObject().put("ID", 205).put("name", "Car 205").put("description", "This is Car 205");

    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return Future.succeededFuture(Set.of(TEST_ENTITY_SET_FQN));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
        return Future.succeededFuture(new EntityWrapper(TEST_ENTITY_SET_FQN,
                List.of(createEntity(ENTITY_DATA_1), createEntity(ENTITY_DATA_2), createEntity(ENTITY_DATA_3))));
    }

    @Override
    public Future<EntityWrapper> createData(DataQuery query, DataContext context) {
        context.responseData().put("Location", ENTITY_URL);
        return Future.succeededFuture(new EntityWrapper(TEST_ENTITY_SET_FQN, createEntity(ENTITY_DATA_1)));
    }

    public static Path getDeclaredEntityModel() {
        return TEST_RESOURCES.resolveRelated("TestService3.csn");
    }
}
