package io.neonbee.test.endpoint.odata;

import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_1;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_2;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_3;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_4;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_5;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_6;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.getDeclaredEntityModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class ODataFilterTest extends ODataEndpointTestBase {
    private ODataRequest oDataRequest;

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(getDeclaredEntityModel());
    }

    private static Map<String, String> filterOf(String value) {
        return Map.of("$filter", value);
    }

    static Stream<Arguments> withFilterOptions() {
        Stream<Arguments> inFunction = Stream.of(Arguments.of(filterOf("KeyPropertyString in ('3', '1')"), List.of()),
                Arguments.of(filterOf("KeyPropertyString in ('id.3', 'id-1')"),
                        List.of(EXPECTED_ENTITY_DATA_2, EXPECTED_ENTITY_DATA_4)));

        Stream<Arguments> stringFunctions = Stream.of(
                Arguments.of(filterOf("contains(PropertyString100,'separat')"), List.of(EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf(
                        "tolower(PropertyString100) eq 'li europan lingues es membres del sam familie. lor separat existentie es un myth. por scientie, musi'"),
                        List.of(EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf(
                        "toupper(PropertyString100) eq 'LI EUROPAN LINGUES ES MEMBRES DEL SAM FAMILIE. LOR SEPARAT EXISTENTIE ES UN MYTH. POR SCIENTIE, MUSI'"),
                        List.of(EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf("substring(PropertyString100, 98) eq ' m'"), List.of(EXPECTED_ENTITY_DATA_2)),
                Arguments.of(filterOf("substring(PropertyString100, 5, 1) eq 'A'"), List.of(EXPECTED_ENTITY_DATA_5)),
                Arguments.of(filterOf("endswith(PropertyString100, 'musi')"), List.of(EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf("not endswith(PropertyString100, 'musi')"),
                        List.of(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_2, EXPECTED_ENTITY_DATA_4,
                                EXPECTED_ENTITY_DATA_5)),
                Arguments.of(filterOf("startswith(PropertyString100, 'Lorem ipsum dolor sit')"),
                        List.of(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_2)),
                Arguments.of(filterOf("indexof(PropertyString100, ' ipsum dolor sit') eq 5"),
                        List.of(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_2)),

                Arguments.of(filterOf("length(PropertyString100) eq 100 and PropertyDouble eq 2.35"),
                        List.of(EXPECTED_ENTITY_DATA_3)),

                Arguments.of(filterOf("length(PropertyString100) eq 100"),
                        List.of(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_2, EXPECTED_ENTITY_DATA_3,
                                EXPECTED_ENTITY_DATA_4)),
                Arguments.of(filterOf("trim(PropertyString100) eq 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'"),
                        List.of(EXPECTED_ENTITY_DATA_5)),
                Arguments.of(filterOf("trim(PropertyString100) ne 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'"),
                        List.of(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_2, EXPECTED_ENTITY_DATA_3,
                                EXPECTED_ENTITY_DATA_4, EXPECTED_ENTITY_DATA_6)),
                Arguments.of(filterOf(
                        "concat(concat(PropertyString100, '-DELIMITER-'), PropertyString) eq '     ABCDEFGHIJKLMNOPQRSTUVWXYZ -DELIMITER-D'"),
                        List.of(EXPECTED_ENTITY_DATA_5)),
                Arguments.of(filterOf("(contains(PropertyString100,'asdf') or contains(PropertyString100,'asdf'))"),
                        List.of()));

        Stream<Arguments> comperatorsDouble = Stream.of(
                Arguments.of(filterOf("PropertyDouble eq 0.15 or PropertyDouble eq 2.35"),
                        List.of(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf("PropertyDouble gt 2.35"),
                        List.of(EXPECTED_ENTITY_DATA_4, EXPECTED_ENTITY_DATA_5)),
                Arguments.of(filterOf("PropertyDouble ge 2.35"),
                        List.of(EXPECTED_ENTITY_DATA_3, EXPECTED_ENTITY_DATA_4, EXPECTED_ENTITY_DATA_5)),
                Arguments.of(filterOf("PropertyDouble le 2.35"), List.of(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_2,
                        EXPECTED_ENTITY_DATA_3, EXPECTED_ENTITY_DATA_6)));

        Stream<Arguments> comperatorsDate = Stream.of(
                Arguments.of(filterOf("year(PropertyDate) eq 2010"), List.of(EXPECTED_ENTITY_DATA_5)),
                Arguments.of(filterOf("month(PropertyDate) eq 5"), List.of(EXPECTED_ENTITY_DATA_1)),
                Arguments.of(filterOf("day(PropertyDate) eq 22"), List.of(EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf("hour(PropertyDateTime) eq 9"), List.of(EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf("hour(PropertyDateTime) eq 9"), List.of(EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf("minute(PropertyDateTime) eq 46"), List.of(EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf("second(PropertyDateTime) eq 15"), List.of(EXPECTED_ENTITY_DATA_3)),
                Arguments.of(filterOf("fractionalseconds(PropertyDateTime) eq 0.000002"),
                        List.of(EXPECTED_ENTITY_DATA_4)),
                Arguments.of(filterOf("PropertyDate eq 2014-05-24"), List.of(EXPECTED_ENTITY_DATA_1)),
                Arguments.of(filterOf("PropertyDate eq 2010-01-20"), List.of(EXPECTED_ENTITY_DATA_5)),
                Arguments.of(filterOf("PropertyDateTime eq 2013-04-23T08:47:11.000004Z"),
                        List.of(EXPECTED_ENTITY_DATA_2)),
                Arguments.of(filterOf("PropertyDateTime eq 2010-01-20T11:30:05Z"), List.of(EXPECTED_ENTITY_DATA_5)));

        Stream<Arguments> comperatorsInteger = Stream.of(
                Arguments.of(filterOf("PropertyInt32 gt 3"), List.of(EXPECTED_ENTITY_DATA_5, EXPECTED_ENTITY_DATA_6)),
                Arguments.of(filterOf("PropertyInt32 lt 4"), List.of(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_2,
                        EXPECTED_ENTITY_DATA_3, EXPECTED_ENTITY_DATA_4)));

        Stream<Arguments> comperatorsBoolean = Stream.of(Arguments.of(filterOf("PropertyBoolean eq false"), List
                .of(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_3, EXPECTED_ENTITY_DATA_5, EXPECTED_ENTITY_DATA_6)));

        return Stream.of(inFunction, stringFunctions, comperatorsDouble, comperatorsDate, comperatorsInteger,
                comperatorsBoolean).flatMap(i -> i);
    }

    @BeforeEach
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void setUp(VertxTestContext testContext) {
        deployVerticle(new TestService1EntityVerticle()).onComplete(testContext.succeedingThenComplete());
        oDataRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN);
    }

    @ParameterizedTest(name = "{index}: with query {0}")
    @MethodSource("withFilterOptions")
    @DisplayName("Test $filter")
    @Timeout(value = 3, timeUnit = TimeUnit.SECONDS)
    void testFilter(Map<String, String> query, List<JsonObject> expected, VertxTestContext testContext) {
        oDataRequest.setQuery(query);
        assertODataEntitySetContainsExactly(requestOData(oDataRequest), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 3, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test /$count with $filter")
    void testCountWithFilter(VertxTestContext testContext) {
        oDataRequest.setQuery(Map.of("$filter", "KeyPropertyString eq 'id-1'")).setCount();
        assertOData(requestOData(oDataRequest), "1", testContext).onComplete(testContext.succeedingThenComplete());
    }
}
