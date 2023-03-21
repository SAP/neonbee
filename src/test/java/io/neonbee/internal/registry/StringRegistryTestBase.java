package io.neonbee.internal.registry;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.internal.helper.AsyncHelper;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
abstract class StringRegistryTestBase {
    private Registry<String> stringRegistry;

    private static final List<String> LIST_ONE_VALUE = List.of("value1");

    private static final List<String> LIST_TWO_VALUES = List.of("value1", "value2");

    private static final Map<String, List<String>> MAP_WITH_ONE_KEY_AND_ONE_VALUE_EACH = Map.of("key1", LIST_ONE_VALUE);

    private static final Map<String, List<String>> MAP_WITH_TWO_KEYS_AND_TWO_VALUES_EACH =
            Map.of("key1", LIST_TWO_VALUES, "key2", LIST_TWO_VALUES);

    protected abstract Future<Registry<String>> createRegistry(Vertx vertx);

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        createRegistry(vertx).map(stringRegistry -> this.stringRegistry = stringRegistry)
                .onComplete(testContext.succeedingThenComplete());
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete());
    }

    static Stream<Arguments> testRegister() {
        Arguments oneKeyOneValue = Arguments.of("oneKeyOneValue", MAP_WITH_ONE_KEY_AND_ONE_VALUE_EACH,
                null);
        Arguments oneKeyTwoValues =
                Arguments.of("oneKeyTwoValues", Map.of("key1", LIST_TWO_VALUES), null);
        Arguments twoKeysTwoValues = Arguments.of("twoKeysTwoValues", MAP_WITH_TWO_KEYS_AND_TWO_VALUES_EACH, null);
        Arguments oneKeyTwoSimilarValues =
                Arguments.of("oneKeyTwoSimilarValues", Map.of("key1", List.of("value1", "value1")),
                        MAP_WITH_ONE_KEY_AND_ONE_VALUE_EACH);

        return Stream.of(oneKeyOneValue, oneKeyTwoValues, twoKeysTwoValues, oneKeyTwoSimilarValues);
    }

    @MethodSource("testRegister")
    @DisplayName("Test register(String, T)")
    @ParameterizedTest(name = "{index}: with {0}")
    void testRegisterSingle(String scenario, Map<String, List<String>> dataToFill, Map<String, List<String>> expected,
            VertxTestContext testContext) {
        Map<String, List<String>> effectiveExpected = expected == null ? dataToFill : expected;
        Checkpoint entriesVerified = testContext.checkpoint(dataToFill.size());

        List<Future<Void>> registerFutures = new ArrayList<>();
        dataToFill.forEach((key, values) -> registerFutures.add(stringRegistry.register(key, values)));
        AsyncHelper.allComposite(registerFutures).<Void>mapEmpty()
                .onSuccess(verify(stringRegistry, effectiveExpected, entriesVerified, testContext))
                .onFailure(testContext::failNow);
    }

    @MethodSource("testRegister")
    @DisplayName("Test register(String, List<T>)")
    @ParameterizedTest(name = "{index}: with {0}")
    void testRegisterList(String scenario, Map<String, List<String>> dataToFill, Map<String, List<String>> expected,
            VertxTestContext testContext) {
        Map<String, List<String>> effectiveExpected = expected == null ? dataToFill : expected;
        Checkpoint entriesVerified = testContext.checkpoint(dataToFill.size());
        fillRegistry(stringRegistry, dataToFill)
                .onSuccess(verify(stringRegistry, effectiveExpected, entriesVerified, testContext))
                .onFailure(testContext::failNow);
    }

    static Stream<Arguments> testUnregister() {
        Arguments oneKeyOneValue = Arguments.of("oneKeyOneValue", Map.of("key2", LIST_ONE_VALUE),
                Map.of("key1", LIST_TWO_VALUES, "key2", List.of("value2")));
        Arguments oneKeyTwoSimilarValues =
                Arguments.of("oneKeyTwoSimilarValues", Map.of("key2", List.of("value1", "value1")),
                        Map.of("key1", LIST_TWO_VALUES, "key2", List.of("value2")));
        Arguments oneKeyTwoValues = Arguments.of("oneKeyTwoValues", Map.of("key2", LIST_TWO_VALUES),
                Map.of("key1", LIST_TWO_VALUES, "key2", List.of()));
        return Stream.of(oneKeyOneValue, oneKeyTwoSimilarValues, oneKeyTwoValues);
    }

    @MethodSource("testUnregister")
    @DisplayName("Test unregister(String, T)")
    @ParameterizedTest(name = "{index}: with {0}")
    void testUnregisterSingle(String scenario, Map<String, List<String>> dataToUnregister,
            Map<String, List<String>> expected,
            VertxTestContext testContext) {
        Checkpoint entriesVerified = testContext.checkpoint(expected.size());
        fillRegistry(stringRegistry, MAP_WITH_TWO_KEYS_AND_TWO_VALUES_EACH)
                .compose(ignore -> removeFromRegistry(stringRegistry, dataToUnregister))
                .onSuccess(verify(stringRegistry, expected, entriesVerified, testContext))
                .onFailure(testContext::failNow);
    }

    @MethodSource("testUnregister")
    @DisplayName("Test unregister(String, List<T>)")
    @ParameterizedTest(name = "{index}: with {0}")
    void testUnregisterList(String scenario, Map<String, List<String>> dataToUnregister,
            Map<String, List<String>> expected,
            VertxTestContext testContext) {
        Checkpoint entriesVerified = testContext.checkpoint(expected.size());
        fillRegistry(stringRegistry, MAP_WITH_TWO_KEYS_AND_TWO_VALUES_EACH)
                .compose(ignore -> {
                    List<Future<Void>> unregisterFutures = new ArrayList<>();
                    dataToUnregister
                            .forEach((key, values) -> unregisterFutures.add(stringRegistry.unregister(key, values)));
                    return AsyncHelper.allComposite(unregisterFutures).<Void>mapEmpty();
                })
                .onSuccess(verify(stringRegistry, expected, entriesVerified, testContext))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Test getKeys")
    void testGetKeys(VertxTestContext testContext) {
        Checkpoint checkTwoKeys = testContext.checkpoint(2);
        Checkpoint checkOneKey = testContext.checkpoint();
        Checkpoint checkNoneKey = testContext.checkpoint();

        BiFunction<Set<String>, Checkpoint, Future<Void>> keySetVerifier = (expectedKeys, checkpoint) -> {
            return stringRegistry.getKeys().onSuccess(keys -> testContext.verify(() -> {
                assertThat(keys).containsExactlyElementsIn(expectedKeys);
                checkpoint.flag();
            })).mapEmpty();
        };

        fillRegistry(stringRegistry, MAP_WITH_TWO_KEYS_AND_TWO_VALUES_EACH)
                .compose(v -> keySetVerifier.apply(Set.of("key1", "key2"), checkTwoKeys))
                .compose(v -> removeFromRegistry(stringRegistry, MAP_WITH_ONE_KEY_AND_ONE_VALUE_EACH))
                .compose(v -> keySetVerifier.apply(Set.of("key1", "key2"), checkTwoKeys))
                .compose(v -> removeFromRegistry(stringRegistry, Map.of("key1", LIST_TWO_VALUES)))
                .compose(v -> keySetVerifier.apply(Set.of("key2"), checkOneKey))
                .compose(v -> removeFromRegistry(stringRegistry, Map.of("key2", LIST_TWO_VALUES)))
                .compose(v -> keySetVerifier.apply(Set.of(), checkNoneKey)).onFailure(testContext::failNow);
    }

    private static Handler<Void> verify(Registry<String> stringRegistry, Map<String, List<String>> expected,
            Checkpoint entriesVerified, VertxTestContext testContext) {
        return ignore -> expected.forEach(
                (key, valueList) -> stringRegistry.get(key)
                        .onSuccess(retrievedValues -> testContext.verify(() -> {
                            assertThat(retrievedValues).containsExactlyElementsIn(valueList);
                            entriesVerified.flag();
                        })));
    }

    private static Future<Void> fillRegistry(Registry<String> registry, Map<String, List<String>> testData) {
        List<Future<Void>> registerFutures = new ArrayList<>();
        testData.forEach((key, values) -> registerFutures.add(registry.register(key, values)));
        return AsyncHelper.allComposite(registerFutures).mapEmpty();
    }

    private static Future<Void> removeFromRegistry(Registry<String> registry,
            Map<String, List<String>> dataToUnregister) {
        List<Future<Void>> unregisterFutures = new ArrayList<>();
        dataToUnregister.forEach((key, valueList) -> valueList
                .forEach(value -> unregisterFutures.add(registry.unregister(key, value))));
        return AsyncHelper.allComposite(unregisterFutures).mapEmpty();
    }
}
