package io.neonbee.test.endpoint.odata.verticle;

import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.vertx.core.Future.succeededFuture;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class NavPropsCategoriesEntityVerticle extends EntityVerticle {
    public static final FullQualifiedName CATEGORIES_ENTITY_SET_FQN =
            new FullQualifiedName("io.neonbee.test.NavProbs", "Categories");

    public static final String PROPERTY_NAME_ID = "ID";

    public static final String PROPERTY_NAME_NAME = "name";

    public static final String PROPERTY_NAME_PRODUCTS = "products";

    public static final JsonObject FOOD_CATEGORY = createCategory(1, "Food");

    public static final JsonObject MOTORCYCLE_CATEGORY = createCategory(2, "Motorcycles");

    public static final List<JsonObject> ALL_CATEGORIES = List.of(FOOD_CATEGORY, MOTORCYCLE_CATEGORY);

    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return succeededFuture(Set.of(CATEGORIES_ENTITY_SET_FQN));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataMap require, DataContext context) {
        List<Entity> allEntities = ALL_CATEGORIES.stream().map(NavPropsCategoriesEntityVerticle::createCategory)
                .collect(Collectors.toList());
        return succeededFuture(new EntityWrapper(CATEGORIES_ENTITY_SET_FQN, allEntities));
    }

    public static Entity createCategory(JsonObject category) {
        return new Entity()
                .addProperty(new Property(null, PROPERTY_NAME_ID, ValueType.PRIMITIVE,
                        category.getInteger(PROPERTY_NAME_ID)))
                .addProperty(new Property(null, PROPERTY_NAME_NAME, ValueType.PRIMITIVE,
                        category.getString(PROPERTY_NAME_NAME)));
    }

    public static JsonObject createCategory(int id, String name) {
        return new JsonObject().put(PROPERTY_NAME_ID, id).put(PROPERTY_NAME_NAME, name);
    }

    public static JsonObject addProductsToCategory(JsonObject category, List<JsonObject> expanedProducts) {
        return category.copy().put(PROPERTY_NAME_PRODUCTS, expanedProducts);
    }

    public static Path getDeclaredEntityModel() {
        return TEST_RESOURCES.resolveRelated("NavigationProperty.csn");
    }
}
