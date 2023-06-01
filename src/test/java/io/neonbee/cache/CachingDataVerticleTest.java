package io.neonbee.cache;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.testing.EqualsTester;

import io.neonbee.cache.CachingDataVerticle.CacheTuple;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class CachingDataVerticleTest extends DataVerticleTestBase {
    @BeforeEach
    void reset() {
        CachingDataVerticle.CACHES.clear();
    }

    @Test
    @DisplayName("Should compute an unique but deterministic cache key with the default implementation")
    void getCacheKeyTest() {
        CachingDataVerticle<JsonArray> testVerticle = new CachingDataVerticle<>() {
            @Override
            public String getName() {
                return "TestCachingVerticle";
            }

            @Override
            public Future<Collection<DataRequest>> requireDataForCaching(DataQuery query, DataContext context) {
                return null;
            }

            @Override
            public Future<JsonArray> retrieveDataToCache(DataQuery query, DataMap require, DataContext context) {
                return null;
            }
        };

        DataContext context = mock(DataContext.class);
        JsonObject userPrincipal = new JsonObject().put("user_name", "Lord Citrange");
        when(context.userPrincipal()).thenReturn(userPrincipal);

        DataContext context2 = mock(DataContext.class);
        JsonObject userPrincipal2 = new JsonObject().put("user_name", "Lord Citrange");
        when(context.userPrincipal()).thenReturn(userPrincipal2);

        // Check that the ID changes with a different query and same with the same query
        Object got1 = testVerticle.getCacheKey(new DataQuery(), context).result();
        assertThat(got1).isEqualTo(testVerticle.getCacheKey(new DataQuery(), context).result());
        assertThat(got1).isNotEqualTo(testVerticle.getCacheKey(new DataQuery(), context2).result());

        Object got2 = testVerticle.getCacheKey(new DataQuery("/some/path"), context).result();
        assertThat(got2).isNotEqualTo(got1);
        assertThat(got2).isEqualTo(testVerticle.getCacheKey(new DataQuery("/some/path"), context).result());
        assertThat(got2).isNotEqualTo(testVerticle.getCacheKey(new DataQuery("/some/path"), context2).result());

        Object got3 = testVerticle.getCacheKey(new DataQuery().setParameter("foo", "bar"), context).result();
        assertThat(got3).isNotEqualTo(got1);
        assertThat(got3).isNotEqualTo(got2);
        assertThat(got3)
                .isEqualTo(testVerticle.getCacheKey(new DataQuery().setParameter("foo", "bar"), context).result());
        assertThat(got3)
                .isNotEqualTo(testVerticle.getCacheKey(new DataQuery().setParameter("foo", "bar"), context2).result());

        JsonObject body = new JsonObject();
        Object got4 = testVerticle.getCacheKey(new DataQuery().setBody(body.toBuffer()), context).result();
        assertThat(got4).isNotEqualTo(got1);
        assertThat(got4).isNotEqualTo(got2);
        assertThat(got4).isNotEqualTo(got3);
        assertThat(got4)
                .isEqualTo(testVerticle.getCacheKey(new DataQuery().setBody(body.toBuffer()), context).result());
        assertThat(got4)
                .isNotEqualTo(testVerticle.getCacheKey(new DataQuery().setBody(body.toBuffer()), context2).result());
    }

    static class MyCachingVerticle extends CachingDataVerticle<String> {
        @Override
        public Future<String> retrieveDataToCache(DataQuery query, DataMap require, DataContext context) {
            return Future.succeededFuture("SUCCESS");
        }

        @Override
        public String getName() {
            return "MY_CACHING_TEST";
        }
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("requireData should call requireDataBeforeCache on cache miss")
    void requireDataRequiresDataOnCacheMiss(VertxTestContext testContext) {
        Checkpoint requireCheckpoint = testContext.checkpoint();
        Checkpoint retrieveCheckpoint = testContext.checkpoint();

        CachingDataVerticle<JsonArray> testClass = new CachingDataVerticle<>() {
            @Override
            public String getName() {
                return "TestCachingVerticle";
            }

            @Override
            public Future<Collection<DataRequest>> requireDataForCaching(DataQuery query, DataContext context) {
                requireCheckpoint.flag();
                return Future.succeededFuture(List.of());
            }

            @Override
            public Future<JsonArray> retrieveDataToCache(DataQuery query, DataMap require, DataContext context) {
                retrieveCheckpoint.flag();
                return Future.succeededFuture();
            }
        };
        deployVerticle(testClass).compose(s -> assertData(requestData(new DataRequest(testClass.getName())), resp -> {
            // When the request returned the cachedData methods should already be called
            // don't wait for a timeout if not and fail immediately
            if (!testContext.completed()) {
                testContext.failNow(new Throwable("CachedData methods not called"));
            }
        }, testContext));
    }

    @Test
    void testCacheTupleEquals() {
        CacheTuple t = new CacheTuple("foo", "bar", "bla");
        CacheTuple t2 = new CacheTuple("foo", "bar", "bla");
        new EqualsTester().addEqualityGroup(t).testEquals();
        assertThat(t).isEqualTo(t2);
    }

    @Test
    void testRequireDataBeforeCacheIsEmpty() {
        MyCachingVerticle v = new MyCachingVerticle();
        assertThat(v.requireDataForCaching(null, null).result()).isEmpty();
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Neither requireDataBeforeCache nor retrieveDataToCache should be called on cache hit")
    void dontCallDataOnCacheHit(VertxTestContext testContext) {
        JsonArray expected = new JsonArray(List.of(1, 1));

        CachingDataVerticle<JsonArray> testClass = new CachingDataVerticle<>() {
            int requireCallCounter;

            int retrieveCallCounter;

            @Override
            public String getName() {
                return "TestCachingVerticle";
            }

            @Override
            public Future<Collection<DataRequest>> requireDataForCaching(DataQuery query, DataContext context) {
                if (requireCallCounter++ > 0) {
                    testContext.failNow(new Throwable("requireDataBeforeCache should only be called once"));
                }
                return Future.succeededFuture(List.of());
            }

            @Override
            public Future<JsonArray> retrieveDataToCache(DataQuery query, DataMap require, DataContext context) {
                if (retrieveCallCounter++ > 0) {
                    testContext.failNow(new Throwable("retrieveDataToCache should only be called once"));
                }
                return Future.succeededFuture(new JsonArray(List.of(requireCallCounter, retrieveCallCounter)));
            }
        };

        DataRequest dr = new DataRequest(testClass.getName());
        deployVerticle(testClass).compose(id -> assertDataEquals(requestData(dr), expected, testContext))
                .onComplete(testContext.succeeding(v ->
                // Request *again* and check response is the cached one
                assertDataEquals(requestData(dr), expected, testContext)))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Multiple parallel request should be coalesced into one request, if no data is in the cache")
    void coalescedMultipleParallelRequests(VertxTestContext testContext) {
        CachingDataVerticle<Integer> testClass = new CachingDataVerticle<>() {
            int retrieveCallCounter;

            @Override
            public String getName() {
                return "TestCachingVerticle";
            }

            @Override
            public Future<Collection<DataRequest>> requireDataForCaching(DataQuery query, DataContext context) {
                return Future.succeededFuture(List.of());
            }

            @Override
            public Future<Integer> retrieveDataToCache(DataQuery query, DataMap require, DataContext context) {
                retrieveCallCounter++;
                return Future.future(promise -> vertx.setTimer(100, promise::complete))
                        .map(timerId -> retrieveCallCounter);
            }
        };

        DataRequest dr = new DataRequest(testClass.getName());
        deployVerticle(testClass)
                .compose(id -> CompositeFuture.all(assertDataEquals(requestData(dr), 1, testContext),
                        assertDataEquals(requestData(dr), 1, testContext)))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Multiple requests should not be coalesced into one request, if no data is in the cache and coalescing is turned off")
    void doNotCoalescedMultipleParallelRequests(VertxTestContext testContext) {
        CachingDataVerticle<Integer> testClass = new CachingDataVerticle<>(10, TimeUnit.SECONDS, 0) {
            int retrieveCallCounter;

            @Override
            public String getName() {
                return "TestCachingVerticle";
            }

            @Override
            public Future<Collection<DataRequest>> requireDataForCaching(DataQuery query, DataContext context) {
                return Future.succeededFuture(List.of());
            }

            @Override
            public Future<Integer> retrieveDataToCache(DataQuery query, DataMap require, DataContext context) {
                retrieveCallCounter++;
                return Future.future(promise -> vertx.setTimer(100, promise::complete))
                        .map(timerId -> retrieveCallCounter);
            }
        };

        DataRequest dr = new DataRequest(testClass.getName());
        deployVerticle(testClass)
                .compose(id -> CompositeFuture.all(assertDataEquals(requestData(dr), 2, testContext),
                        assertDataEquals(requestData(dr), 2, testContext)))
                .onComplete(testContext.succeeding(v ->
                // Once the data is buffered though, and we *again* do a request and check response is the cached one
                assertDataEquals(requestData(dr), 2, testContext)))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @DisplayName("If multiple parallel requests should be coalesced into one request and the request takes too long, it should trigger another request anyways, if no data is in the cache")
    void coalescedMultipleParallelRequestsFallback(VertxTestContext testContext) {
        CachingDataVerticle<Integer> testClass = new CachingDataVerticle<>(5, TimeUnit.MINUTES, 1000) {
            int retrieveCallCounter;

            @Override
            public String getName() {
                return "TestCachingVerticle";
            }

            @Override
            public Future<Collection<DataRequest>> requireDataForCaching(DataQuery query, DataContext context) {
                return Future.succeededFuture(List.of());
            }

            @Override
            public Future<Integer> retrieveDataToCache(DataQuery query, DataMap require, DataContext context) {
                return Future.future(
                        promise -> vertx.setTimer(retrieveCallCounter++ == 0 ? 2000 : 100, promise::complete))
                        .map(timerId -> retrieveCallCounter);
            }
        };

        DataRequest dr = new DataRequest(testClass.getName());
        deployVerticle(testClass)
                .compose(id -> CompositeFuture.all(assertDataEquals(requestData(dr), 2, testContext),
                        assertDataEquals(requestData(dr), 2, testContext)))
                .onComplete(testContext.succeedingThenComplete());
    }
}
