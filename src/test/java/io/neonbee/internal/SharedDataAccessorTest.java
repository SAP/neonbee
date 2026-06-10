package io.neonbee.internal;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class SharedDataAccessorTest {

    private SharedDataAccessor accessor;

    @BeforeEach
    void setUp(Vertx vertx) {
        accessor = new SharedDataAccessor(vertx, SharedDataAccessorTest.class);
    }

    @Test
    @DisplayName("getAsyncMap with name")
    void testGetAsyncMap(Vertx vertx, VertxTestContext testContext) {
        accessor.<String, String>getAsyncMap("testMap")
                .onComplete(testContext.succeeding(map -> testContext.verify(() -> {
                    assertThat(map).isNotNull();
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("getAsyncMap with handler (no name)")
    void testGetAsyncMapHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.<String, String>getAsyncMap(testContext.succeeding(map -> testContext.verify(() -> {
            assertThat(map).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getAsyncMap with name and handler")
    @SuppressWarnings({ "deprecation", "removal" })
    void testGetAsyncMapNameHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.<String, String>getAsyncMap("named", testContext.succeeding(map -> testContext.verify(() -> {
            assertThat(map).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalAsyncMap future (no name)")
    void testGetLocalAsyncMap(Vertx vertx, VertxTestContext testContext) {
        accessor.<String, String>getLocalAsyncMap()
                .onComplete(testContext.succeeding(map -> testContext.verify(() -> {
                    assertThat(map).isNotNull();
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("getLocalAsyncMap with name")
    void testGetLocalAsyncMapNamed(Vertx vertx, VertxTestContext testContext) {
        accessor.<String, String>getLocalAsyncMap("localMap")
                .onComplete(testContext.succeeding(map -> testContext.verify(() -> {
                    assertThat(map).isNotNull();
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("getLocalAsyncMap with handler (no name)")
    void testGetLocalAsyncMapHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.<String, String>getLocalAsyncMap(testContext.succeeding(map -> testContext.verify(() -> {
            assertThat(map).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalAsyncMap with name and handler")
    @SuppressWarnings({ "deprecation", "removal" })
    void testGetLocalAsyncMapNameHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.<String, String>getLocalAsyncMap("named", testContext.succeeding(map -> testContext.verify(() -> {
            assertThat(map).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getCounter future (no name)")
    void testGetCounter(Vertx vertx, VertxTestContext testContext) {
        accessor.getCounter().onComplete(testContext.succeeding(counter -> testContext.verify(() -> {
            assertThat(counter).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getCounter with name")
    void testGetCounterNamed(Vertx vertx, VertxTestContext testContext) {
        accessor.getCounter("myCounter").onComplete(testContext.succeeding(counter -> testContext.verify(() -> {
            assertThat(counter).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getCounter with handler")
    void testGetCounterHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getCounter(testContext.succeeding(counter -> testContext.verify(() -> {
            assertThat(counter).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getCounter with name and handler")
    @SuppressWarnings({ "deprecation", "removal" })
    void testGetCounterNameHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getCounter("named", testContext.succeeding(counter -> testContext.verify(() -> {
            assertThat(counter).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalCounter future (no name)")
    void testGetLocalCounter(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalCounter().onComplete(testContext.succeeding(counter -> testContext.verify(() -> {
            assertThat(counter).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalCounter with name")
    void testGetLocalCounterNamed(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalCounter("localCounter")
                .onComplete(testContext.succeeding(counter -> testContext.verify(() -> {
                    assertThat(counter).isNotNull();
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("getLocalCounter with handler")
    void testGetLocalCounterHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalCounter(testContext.succeeding(counter -> testContext.verify(() -> {
            assertThat(counter).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalCounter with name and handler")
    @SuppressWarnings({ "deprecation", "removal" })
    void testGetLocalCounterNameHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalCounter("named", testContext.succeeding(counter -> testContext.verify(() -> {
            assertThat(counter).isNotNull();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLock future (no name)")
    void testGetLock(Vertx vertx, VertxTestContext testContext) {
        accessor.getLock().onComplete(testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLock with name")
    void testGetLockNamed(Vertx vertx, VertxTestContext testContext) {
        accessor.getLock("myLock").onComplete(testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLock with handler")
    void testGetLockHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLock(testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLock with name and handler")
    @SuppressWarnings({ "deprecation", "removal" })
    void testGetLockNameHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLock("named", testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalLock future (no name)")
    void testGetLocalLock(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalLock().onComplete(testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalLock with name")
    void testGetLocalLockNamed(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalLock("localLock").onComplete(testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalLock with handler")
    void testGetLocalLockHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalLock(testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalLock with name and handler")
    @SuppressWarnings({ "deprecation", "removal" })
    void testGetLocalLockNameHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalLock("named", testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLockWithTimeout future (no name)")
    void testGetLockWithTimeout(Vertx vertx, VertxTestContext testContext) {
        accessor.getLockWithTimeout(1000L).onComplete(testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLockWithTimeout with name")
    void testGetLockWithTimeoutNamed(Vertx vertx, VertxTestContext testContext) {
        accessor.getLockWithTimeout("timedLock", 1000L)
                .onComplete(testContext.succeeding(lock -> testContext.verify(() -> {
                    assertThat(lock).isNotNull();
                    lock.release();
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("getLockWithTimeout with handler")
    void testGetLockWithTimeoutHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLockWithTimeout(1000L, testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLockWithTimeout with name and handler")
    @SuppressWarnings({ "deprecation", "removal" })
    void testGetLockWithTimeoutNameHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLockWithTimeout("named", 1000L, testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalLockWithTimeout future (no name)")
    void testGetLocalLockWithTimeout(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalLockWithTimeout(1000L).onComplete(testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalLockWithTimeout with name")
    void testGetLocalLockWithTimeoutNamed(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalLockWithTimeout("localTimedLock", 1000L)
                .onComplete(testContext.succeeding(lock -> testContext.verify(() -> {
                    assertThat(lock).isNotNull();
                    lock.release();
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("getLocalLockWithTimeout with handler")
    void testGetLocalLockWithTimeoutHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalLockWithTimeout(1000L, testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalLockWithTimeout with name and handler")
    @SuppressWarnings({ "deprecation", "removal" })
    void testGetLocalLockWithTimeoutNameHandler(Vertx vertx, VertxTestContext testContext) {
        accessor.getLocalLockWithTimeout("named", 1000L, testContext.succeeding(lock -> testContext.verify(() -> {
            assertThat(lock).isNotNull();
            lock.release();
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("getLocalMap (no name)")
    void testGetLocalMap(Vertx vertx) {
        assertThat(accessor.<String, String>getLocalMap()).isNotNull();
    }

    @Test
    @DisplayName("getLocalMap with name")
    void testGetLocalMapNamed(Vertx vertx) {
        assertThat(accessor.<String, String>getLocalMap("namedLocal")).isNotNull();
    }

    @Test
    @DisplayName("SharedDataAccessor with SharedData constructor")
    void testSharedDataConstructor(Vertx vertx) {
        SharedDataAccessor fromSharedData = new SharedDataAccessor(vertx.sharedData(), SharedDataAccessorTest.class);
        assertThat(fromSharedData.<String, String>getLocalMap()).isNotNull();
    }
}
