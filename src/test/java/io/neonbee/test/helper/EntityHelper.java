package io.neonbee.test.helper;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

public final class EntityHelper {

    private EntityHelper() {
        // helper class no need to instantiate
    }

    /**
     * This method loops over the elements of the passed {@link JsonObject}, transform the entries into a
     * {@link Property} with {@link ValueType#PRIMITIVE} and adds them into the returned {@link Entity}.
     *
     * @param entityData The data to put into the new entity object
     * @return A new {@link Entity} that contains the passed properties
     */
    public static Entity createEntity(JsonObject entityData) {
        Entity entity = new Entity();
        entityData.fieldNames().stream().forEach(fieldName -> entity
                .addProperty(new Property(null, fieldName, ValueType.PRIMITIVE, entityData.getValue(fieldName))));
        return entity;
    }

    /**
     * This method transforms the passed entities in a simple key/value map and compares the maps.
     *
     * @param expectedEntity The expected entity
     * @param entity         The entity to test
     * @param ignoreFields   Fields to be ignored for evaluation
     * @param testContext    The {@link VertxTestContext} to fail the test in case of failure
     */
    public static void compareLazy(Entity expectedEntity, Entity entity, VertxTestContext testContext,
            String... ignoreFields) {
        testContext.verify(() -> {
            List<String> ignore = List.of(ignoreFields);
            assertThat(toMap(entity, ignore)).containsExactlyEntriesIn(toMap(expectedEntity, ignore));
        });
    }

    private static Map<String, Object> toMap(Entity entity, List<String> ignoreFields) {
        Map<String, Object> mapToReturn = new HashMap<>();
        entity.getProperties().forEach(prop -> {
            if (!ignoreFields.contains(prop.getName())) {
                mapToReturn.put(prop.getName(), prop.getValue());
            }
        });
        return mapToReturn;
    }
}
