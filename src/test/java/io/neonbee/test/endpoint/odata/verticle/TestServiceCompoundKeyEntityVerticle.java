package io.neonbee.test.endpoint.odata.verticle;

import static io.neonbee.test.helper.EntityHelper.createEntity;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class TestServiceCompoundKeyEntityVerticle extends EntityVerticle {
    public static final FullQualifiedName TEST_ENTITY_SET_FQN =
            new FullQualifiedName("io.neonbee.compoundkey.TestServiceCompoundKey", "TestCars");

    public static final JsonObject ENTITY_DATA_1 = createEntityData(0, "2020-02-02", "Car 0", "The first car");

    public static final JsonObject ENTITY_DATA_2 = createEntityData(1, "2020-02-02", "Car 1", "The second car");

    public static final JsonObject ENTITY_DATA_3 = createEntityData(2, "2020-02-02", "Car 2", "The third car");

    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return Future.succeededFuture(Set.of(TEST_ENTITY_SET_FQN));
    }

    @Override
    public Future<EntityWrapper> updateData(DataQuery query, DataContext context) {
        return Future.succeededFuture(new EntityWrapper(TEST_ENTITY_SET_FQN, (Entity) null));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
        return Future.succeededFuture(new EntityWrapper(TEST_ENTITY_SET_FQN,
                List.of(createEntity(ENTITY_DATA_1), createEntity(ENTITY_DATA_2), createEntity(ENTITY_DATA_3))));
    }

    public static Path getDeclaredEntityModel() {
        return TEST_RESOURCES.resolveRelated("TestServiceCompoundKey.csn");
    }

    public static JsonObject createEntityData(int id, String date, String name, String description) {
        long epochSeconds = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).getEpochSecond();
        JsonObject entityData = new JsonObject();
        entityData.put("ID", id);
        entityData.put("date", epochSeconds);
        entityData.put("name", name);
        entityData.put("description", description);
        return entityData;
    }
}
