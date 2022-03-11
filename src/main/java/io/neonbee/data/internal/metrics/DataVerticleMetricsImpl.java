package io.neonbee.data.internal.metrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import com.google.common.collect.Iterables;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Future;

public class DataVerticleMetricsImpl implements DataVerticleMetrics {

    private static final ImmutableTag SUCCEEDED_TAG = new ImmutableTag("succeeded", "true");

    private static final ImmutableTag FAILED_TAG = new ImmutableTag("succeeded", "false");

    private final Map<Meter.Id, LongAdder> activeRequestsMap = new ConcurrentHashMap<>();

    private final MeterRegistry registry;

    DataVerticleMetricsImpl(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void reportNumberOfRequests(String name, String description, List<Tag> tags) {
        Counter.builder(name).description(description).tags(tags).register(registry).increment();
    }

    @Override
    public void reportActiveRequestsGauge(String name, String description, List<Tag> tags, Future<?> future) {
        LongAdder longAdder = getGaugeLongAdder(name, description, tags, registry);
        longAdder.increment();
        future.onComplete(event -> {
            longAdder.decrement();
        });
    }

    private LongAdder getGaugeLongAdder(String name, String description, List<Tag> tags, MeterRegistry registry) {
        LongAdder longAdder = new LongAdder();
        Gauge gauge = Gauge.builder(name, longAdder, LongAdder::doubleValue).description(description).tags(tags)
                .register(registry);
        activeRequestsMap.computeIfAbsent(gauge.getId(), id -> longAdder);
        return longAdder;
    }

    @Override
    public void reportStatusCounter(String name, String description, Iterable<Tag> tags, Future<?> future) {
        future.onComplete(data -> {
            Counter.builder(name).description("succeeded response count")
                    .tags(Iterables.concat(tags, List.of(data.succeeded() ? SUCCEEDED_TAG : FAILED_TAG)))
                    .register(registry).increment();
        });
    }

    @Override
    public void reportTimingMetric(String name, String description, Iterable<Tag> tags, Future<?> future) {
        long start = System.nanoTime();
        future.onComplete(data -> reportTimeMetric(name, description, tags, start));
    }

    private void reportTimeMetric(String name, String description, Iterable<Tag> tags, long start) {
        long time = System.nanoTime() - start;

        Timer timer = Timer.builder(name).description(description).tags(tags).register(registry);
        timer.record(time, TimeUnit.NANOSECONDS);
    }

}
