package io.neonbee.test.helper;

import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public final class ConcurrentHelper {

    private ConcurrentHelper() {
        // helper class no need to instantiate
    }

    /**
     * This method returns a future after a delay.
     *
     * @param vertx The related Vert.x instance
     * @param unit  Time unit for the delay
     * @param value value for the delay
     *
     * @return A succeeded future.
     */
    public static Future<Void> waitFor(Vertx vertx, TimeUnit unit, long value) {
        return waitFor(vertx, unit.toMillis(value));
    }

    /**
     * This method returns a future after a delay.
     *
     * @param vertx  The related Vert.x instance
     * @param millis Milliseconds to wait
     *
     * @return A succeeded future.
     */
    public static Future<Void> waitFor(Vertx vertx, long millis) {
        return Future.future(p -> vertx.setTimer(millis, along -> p.complete()));
    }
}
