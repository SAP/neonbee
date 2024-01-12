package io.neonbee.test.endpoint.odata.verticle;

import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.vertx.core.Future.succeededFuture;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

public class NavPropsProductsEntityVerticle extends EntityVerticle {
    public static final FullQualifiedName PRODUCTS_ENTITY_SET_FQN =
            new FullQualifiedName("io.neonbee.test.NavProbs", "Products");

    public static final String PROPERTY_NAME_ID = "ID";

    public static final String PROPERTY_NAME_NAME = "name";

    public static final String PROPERTY_NAME_CATEGORY_ID = "category_ID";

    public static final String PROPERTY_NAME_CATEGORY = "category";

    public static final JsonObject STEAK_PRODUCT = createProduct(1, "Steak", 1);

    public static final JsonObject CHEESE_PRODUCT = createProduct(2, "Cheese", 1);

    public static final JsonObject S_1000_RR_PRODUCT = createProduct(21, "S 1000 RR", 2);

    public static final JsonObject STREET_GLIDE_SPECIAL_PRODUCT = createProduct(22, "Street Glide Special", 2);

    public static final List<JsonObject> ALL_PRODUCTS =
            List.of(STEAK_PRODUCT, CHEESE_PRODUCT, S_1000_RR_PRODUCT, STREET_GLIDE_SPECIAL_PRODUCT);

    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return succeededFuture(Set.of(PRODUCTS_ENTITY_SET_FQN));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataMap require, DataContext context) {
        List<Entity> allEntities =
                ALL_PRODUCTS.stream().map(NavPropsProductsEntityVerticle::createProduct).toList();
        return succeededFuture(new EntityWrapper(PRODUCTS_ENTITY_SET_FQN, allEntities));
    }

    public static Entity createProduct(JsonObject category) {
        return new Entity()
                .addProperty(new Property(null, PROPERTY_NAME_ID, ValueType.PRIMITIVE,
                        category.getInteger(PROPERTY_NAME_ID)))
                .addProperty(new Property(null, PROPERTY_NAME_NAME, ValueType.PRIMITIVE,
                        category.getString(PROPERTY_NAME_NAME)))
                .addProperty(new Property(null, PROPERTY_NAME_CATEGORY_ID, ValueType.PRIMITIVE,
                        category.getInteger(PROPERTY_NAME_CATEGORY_ID)));
    }

    public static JsonObject createProduct(int id, String name, int categoryId) {
        return new JsonObject().put(PROPERTY_NAME_ID, id).put(PROPERTY_NAME_NAME, name).put(
                PROPERTY_NAME_CATEGORY_ID,
                categoryId);
    }

    public static JsonObject addCategoryToProduct(JsonObject product, JsonObject expanedCategory) {
        return product.copy().put(PROPERTY_NAME_CATEGORY, expanedCategory.copy());
    }

    public static Path getDeclaredEntityModel() {
        return TEST_RESOURCES.resolveRelated("NavigationProperty.csn");
    }
}
