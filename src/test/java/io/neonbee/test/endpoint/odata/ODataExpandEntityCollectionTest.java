package io.neonbee.test.endpoint.odata;

import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.ALL_CATEGORIES;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.CATEGORIES_ENTITY_SET_FQN;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.FOOD_CATEGORY;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.MOTORCYCLE_CATEGORY;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.addProductsToCategory;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.ALL_PRODUCTS;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.CHEESE_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.PRODUCTS_ENTITY_SET_FQN;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.STEAK_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.STREET_GLIDE_SPECIAL_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.S_1000_RR_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.addCategoryToProduct;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.getDeclaredEntityModel;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle;
import io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

public class ODataExpandEntityCollectionTest extends ODataEndpointTestBase {
    @Override
    protected List<Path> provideEntityModels() {
        return List.of(getDeclaredEntityModel());
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        CompositeFuture
                .all(deployVerticle(new NavPropsProductsEntityVerticle()),
                        deployVerticle(new NavPropsCategoriesEntityVerticle()))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Expand property 'category' in Products")
    void testExpandCategoryInProducts(VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(PRODUCTS_ENTITY_SET_FQN).setExpandQuery("category");
        List<JsonObject> expected = List.of(addCategoryToProduct(STEAK_PRODUCT, FOOD_CATEGORY),
                addCategoryToProduct(CHEESE_PRODUCT, FOOD_CATEGORY),
                addCategoryToProduct(S_1000_RR_PRODUCT, MOTORCYCLE_CATEGORY),
                addCategoryToProduct(STREET_GLIDE_SPECIAL_PRODUCT, MOTORCYCLE_CATEGORY));

        assertODataEntitySetContainsExactly(requestOData(oDataRequest), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Do not expand property 'category' in Products")
    void testDoNotExpandCategoryInProducts(VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(PRODUCTS_ENTITY_SET_FQN);
        List<JsonObject> expected = ALL_PRODUCTS;

        assertODataEntitySetContainsExactly(requestOData(oDataRequest), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.HOURS)
    @DisplayName("Expand property 'products' in Categories")
    void testExpandProductsInCategory(VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(CATEGORIES_ENTITY_SET_FQN).setExpandQuery("products");
        List<JsonObject> expected = List.of(
                addProductsToCategory(FOOD_CATEGORY, List.of(STEAK_PRODUCT, CHEESE_PRODUCT)),
                addProductsToCategory(MOTORCYCLE_CATEGORY, List.of(S_1000_RR_PRODUCT, STREET_GLIDE_SPECIAL_PRODUCT)));

        assertODataEntitySetContainsExactly(requestOData(oDataRequest), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Do not expand property 'products' in Categories")
    void testDoNotExpandProductsInCategory(VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(CATEGORIES_ENTITY_SET_FQN);
        List<JsonObject> expected = ALL_CATEGORIES;

        assertODataEntitySetContainsExactly(requestOData(oDataRequest), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }
}
