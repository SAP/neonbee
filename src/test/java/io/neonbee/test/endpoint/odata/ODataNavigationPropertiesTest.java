package io.neonbee.test.endpoint.odata;

import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.CATEGORIES_ENTITY_SET_FQN;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.FOOD_CATEGORY;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.MOTORCYCLE_CATEGORY;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle.PROPERTY_NAME_PRODUCTS;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.CHEESE_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.PRODUCTS_ENTITY_SET_FQN;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.PROPERTY_NAME_CATEGORY;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.PROPERTY_NAME_CATEGORY_ID;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.PROPERTY_NAME_ID;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.STEAK_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.STREET_GLIDE_SPECIAL_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.S_1000_RR_PRODUCT;
import static io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle.getDeclaredEntityModel;
import static java.util.stream.Collectors.toList;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.NavPropsCategoriesEntityVerticle;
import io.neonbee.test.endpoint.odata.verticle.NavPropsProductsEntityVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

/**
 * These tests cover the expand option which can be declared with the following kind of request:<br>
 * <br>
 *
 * <pre>
 * http://baseUrl/odata/io.neonbee.test.NavProbs/Categories(2)/products
 * </pre>
 *
 */
class ODataNavigationPropertiesTest extends ODataEndpointTestBase {
    @Override
    protected List<Path> provideEntityModels() {
        return List.of(getDeclaredEntityModel());
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        Future.all(deployVerticle(new NavPropsProductsEntityVerticle()),
                deployVerticle(new NavPropsCategoriesEntityVerticle()))
                .onComplete(testContext.succeedingThenComplete());
    }

    static Stream<Arguments> withProducts() {
        return Stream.of(Arguments.of(STEAK_PRODUCT, FOOD_CATEGORY), Arguments.of(CHEESE_PRODUCT, FOOD_CATEGORY),
                Arguments.of(S_1000_RR_PRODUCT, MOTORCYCLE_CATEGORY),
                Arguments.of(STREET_GLIDE_SPECIAL_PRODUCT, MOTORCYCLE_CATEGORY));
    }

    @ParameterizedTest(name = "{index}: For Product: {0}")
    @MethodSource("withProducts")
    @DisplayName("Navigate to property 'category' in Product")
    void testNavigateToCategoryInProduct(JsonObject product, JsonObject expected, VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(PRODUCTS_ENTITY_SET_FQN)
                .setKey(product.getInteger(PROPERTY_NAME_ID)).setProperty(PROPERTY_NAME_CATEGORY);

        assertODataEntity(requestOData(oDataRequest), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    static Stream<Arguments> withCategories() {
        Function<List<JsonObject>, List<JsonObject>> removeCategoryID = products -> products.stream()
                .map(JsonObject::copy).peek(p -> p.remove(PROPERTY_NAME_CATEGORY_ID)).collect(toList());

        return Stream.of(Arguments.of(FOOD_CATEGORY, removeCategoryID.apply(List.of(STEAK_PRODUCT, CHEESE_PRODUCT))),
                Arguments.of(MOTORCYCLE_CATEGORY,
                        removeCategoryID.apply(List.of(S_1000_RR_PRODUCT, STREET_GLIDE_SPECIAL_PRODUCT))));
    }

    @ParameterizedTest(name = "{index}: For Category: {0}")
    @MethodSource("withCategories")
    @DisplayName("Navigate to property 'products' in Category")
    void testNavigateToProductsInCategory(JsonObject category, List<JsonObject> expected,
            VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(CATEGORIES_ENTITY_SET_FQN).setProperty(PROPERTY_NAME_PRODUCTS)
                .setKey(category.getInteger(PROPERTY_NAME_ID));

        assertODataEntitySetContains(requestOData(oDataRequest), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }
}
