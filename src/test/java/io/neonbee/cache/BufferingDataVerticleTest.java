package io.neonbee.cache;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

@Execution(ExecutionMode.SAME_THREAD)
class BufferingDataVerticleTest extends DataVerticleTestBase {
    AtomicInteger requireDataCount = new AtomicInteger();

    AtomicInteger retrieveDataCount = new AtomicInteger();

    AtomicInteger readDataFromBufferCount = new AtomicInteger();

    AtomicInteger writeDataToBufferCount = new AtomicInteger();

    AtomicBoolean delayResponse = new AtomicBoolean();

    AtomicBoolean respondFromBuffer = new AtomicBoolean();

    BufferingDataVerticle<String> testVerticle;

    DataRequest dr;

    @BeforeEach
    void reset(VertxTestContext testContext) {
        CachingDataVerticle.CACHES.clear();
        requireDataCount.set(0);
        retrieveDataCount.set(0);
        readDataFromBufferCount.set(0);
        writeDataToBufferCount.set(0);
        delayResponse.set(false);
        respondFromBuffer.set(false);
        deployVerticle(testVerticle = new BufferingDataVerticle<>(500, TimeUnit.MILLISECONDS) {
            @Override
            public String getName() {
                return "TestBufferVerticle";
            }

            @Override
            protected Future<Object> getCacheKey(DataQuery query, DataContext context) {
                return Future.succeededFuture("OK");
            }

            @Override
            public Future<Collection<DataRequest>> requireDataForCaching(DataQuery query, DataContext context) {
                requireDataCount.incrementAndGet();
                return Future.succeededFuture(List.of());
            }

            @Override
            public Future<String> retrieveDataToCache(DataQuery query, DataMap require, DataContext context) {
                retrieveDataCount.incrementAndGet();
                Promise<Long> delayPromise = Promise.promise();
                if (delayResponse.get()) {
                    vertx.setTimer(100, delayPromise::complete);
                } else {
                    delayPromise.complete();
                }
                return delayPromise.future().map("Test");
            }

            @Override
            public Future<String> readDataFromBuffer(Object cacheKey, DataContext context) {
                readDataFromBufferCount.incrementAndGet();
                return Future.succeededFuture(respondFromBuffer.get() ? "Test2" : null); // NOPMD
            }

            @Override
            public <U> Future<U> writeDataToBuffer(Object cacheKey, String data, DataContext context) {
                writeDataToBufferCount.incrementAndGet();
                return Future.succeededFuture();
            }
        }).onSuccess(any -> {
            dr = new DataRequest(testVerticle.getName());
        }).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Expect another read from buffer after expiry")
    void expectReadFromBuffer(Vertx vertx, VertxTestContext testContext) {
        requestData(dr)
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(requireDataCount.get()).isEqualTo(1);
                    assertThat(retrieveDataCount.get()).isEqualTo(1);
                    assertThat(readDataFromBufferCount.get()).isEqualTo(1);
                    assertThat(writeDataToBufferCount.get()).isEqualTo(1);
                }))).compose(id -> requestData(dr))
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(requireDataCount.get()).isEqualTo(1);
                    assertThat(retrieveDataCount.get()).isEqualTo(1);
                    assertThat(readDataFromBufferCount.get()).isEqualTo(1);
                    assertThat(writeDataToBufferCount.get()).isEqualTo(1);
                }))).compose(id -> Future.future(promise -> vertx.setTimer(750, promise::complete)))
                .compose(id -> requestData(dr))
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(requireDataCount.get()).isEqualTo(2);
                    assertThat(retrieveDataCount.get()).isEqualTo(2);
                    assertThat(readDataFromBufferCount.get()).isEqualTo(2);
                    assertThat(writeDataToBufferCount.get()).isEqualTo(2);
                })))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Expect only one write to buffer on two parallel requests")
    void expectOneWriteToBuffer(Vertx vertx, VertxTestContext testContext) {
        delayResponse.set(true);
        Future.all(requestData(dr), requestData(dr))
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(requireDataCount.get()).isEqualTo(2);
                    assertThat(retrieveDataCount.get()).isEqualTo(1);
                    assertThat(readDataFromBufferCount.get()).isEqualTo(2);
                    assertThat(writeDataToBufferCount.get()).isEqualTo(1);
                })))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Expect no retrieve data in case data is in buffer")
    void expectOneReadFromBuffer(Vertx vertx, VertxTestContext testContext) {
        respondFromBuffer.set(true);
        delayResponse.set(true);
        Future.all(requestData(dr), requestData(dr))
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(requireDataCount.get()).isEqualTo(0);
                    assertThat(retrieveDataCount.get()).isEqualTo(0);
                    assertThat(readDataFromBufferCount.get()).isEqualTo(1);
                    assertThat(writeDataToBufferCount.get()).isEqualTo(0);

                    delayResponse.set(false);
                }))).compose(id -> requestData(dr))
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(requireDataCount.get()).isEqualTo(0);
                    assertThat(retrieveDataCount.get()).isEqualTo(0);
                    assertThat(readDataFromBufferCount.get()).isEqualTo(1);
                    assertThat(writeDataToBufferCount.get()).isEqualTo(0);
                }))).compose(id -> Future.future(promise -> vertx.setTimer(750, promise::complete)))
                .compose(id -> requestData(dr))
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(requireDataCount.get()).isEqualTo(0);
                    assertThat(retrieveDataCount.get()).isEqualTo(0);
                    assertThat(readDataFromBufferCount.get()).isEqualTo(2);
                    assertThat(writeDataToBufferCount.get()).isEqualTo(0);

                    respondFromBuffer.set(false);
                }))).compose(id -> requestData(dr))
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(requireDataCount.get()).isEqualTo(0);
                    assertThat(retrieveDataCount.get()).isEqualTo(0);
                    assertThat(readDataFromBufferCount.get()).isEqualTo(2);
                    assertThat(writeDataToBufferCount.get()).isEqualTo(0);
                }))).compose(id -> Future.future(promise -> vertx.setTimer(750, promise::complete)))
                .compose(id -> requestData(dr))
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(requireDataCount.get()).isEqualTo(1);
                    assertThat(retrieveDataCount.get()).isEqualTo(1);
                    assertThat(readDataFromBufferCount.get()).isEqualTo(3);
                    assertThat(writeDataToBufferCount.get()).isEqualTo(1);
                })))
                .onComplete(testContext.succeedingThenComplete());
    }
}
