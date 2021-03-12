package io.neonbee.test.endpoint.odata;

import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.CATEGORIES_ENTITY_SET_FQN;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.FOOD_CATEGORY;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.MOTORCYCLE_CATEGORY;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.PROPERTY_NAME_PRODUCTS;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.addProductsToCategory;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.CHEESE_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.PRODUCTS_ENTITY_SET_FQN;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.PROPERTY_NAME_CATEGORY;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.PROPERTY_NAME_ID;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.STEAK_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.STREET_GLIDE_SPECIAL_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.S_1000_RR_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.addCategoryToProduct;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.getDeclaredEntityModel;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle;
import io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

/**
 * These tests cover the expand option which can be declared with the following kind of request:<br>
 * <br>
 *
 * <pre>
 * http://baseUrl/odata/io.neonbee.test.NavProbs/Categories(2)?$expand=products
 * </pre>
 *
 */
public class ODataExpandEntityTest extends ODataEndpointTestBase {
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

    static Stream<Arguments> withProducts() {
        BiFunction<JsonObject, JsonObject, Arguments> buildArgument =
                (product, category) -> Arguments.of(product, addCategoryToProduct(product, category));

        return Stream.of(buildArgument.apply(STEAK_PRODUCT, FOOD_CATEGORY),
                buildArgument.apply(CHEESE_PRODUCT, FOOD_CATEGORY),
                buildArgument.apply(S_1000_RR_PRODUCT, MOTORCYCLE_CATEGORY),
                buildArgument.apply(STREET_GLIDE_SPECIAL_PRODUCT, MOTORCYCLE_CATEGORY));
    }

    @ParameterizedTest(name = "{index}: Expand {0} to {1}")
    @MethodSource("withProducts")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Expand property 'category' in Product")
    void testExpandCategoryInProducts(JsonObject product, JsonObject expected, VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(PRODUCTS_ENTITY_SET_FQN).setExpandQuery(PROPERTY_NAME_CATEGORY)
                .setKey(product.getInteger(PROPERTY_NAME_ID));

        assertODataEntity(requestOData(oDataRequest), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Do not expand property 'category' in a specific Product")
    void testDoNotExpandCategoryInProducts(VertxTestContext testContext) {
        ODataRequest oDataRequest =
                new ODataRequest(PRODUCTS_ENTITY_SET_FQN).setKey(STEAK_PRODUCT.getInteger(PROPERTY_NAME_ID));

        assertODataEntity(requestOData(oDataRequest), STEAK_PRODUCT, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    static Stream<Arguments> withCategories() {
        BiFunction<JsonObject, List<JsonObject>, Arguments> buildArgument =
                (category, products) -> Arguments.of(category, addProductsToCategory(category, products));

        return Stream.of(buildArgument.apply(FOOD_CATEGORY, List.of(STEAK_PRODUCT, CHEESE_PRODUCT)),
                buildArgument.apply(MOTORCYCLE_CATEGORY, List.of(S_1000_RR_PRODUCT, STREET_GLIDE_SPECIAL_PRODUCT)));
    }

    @ParameterizedTest(name = "{index}: Expand {0} to {1}")
    @MethodSource("withCategories")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Expand property 'products' in Category")
    void testExpandProductsInCategory(JsonObject category, JsonObject expected, VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(CATEGORIES_ENTITY_SET_FQN).setExpandQuery(PROPERTY_NAME_PRODUCTS)
                .setKey(category.getInteger(PROPERTY_NAME_ID));

        assertODataEntity(requestOData(oDataRequest), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Do not expand property 'products' in a specific Category")
    void testDoNotExpandProductsInCategory(VertxTestContext testContext) {
        ODataRequest oDataRequest =
                new ODataRequest(CATEGORIES_ENTITY_SET_FQN).setKey(FOOD_CATEGORY.getInteger(PROPERTY_NAME_ID));

        assertODataEntity(requestOData(oDataRequest), FOOD_CATEGORY, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }
}
