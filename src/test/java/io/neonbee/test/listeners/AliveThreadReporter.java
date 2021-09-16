package io.neonbee.test.listeners;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AliveThreadReporter implements TestExecutionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AliveThreadReporter.class);

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testPlanExecutionStarted(TestPlan testPlan) {
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "neonbee-alive-thread-reporter");
            thread.setDaemon(true);
            return thread;
        }).scheduleAtFixedRate(() -> {
            if (LOGGER.isDebugEnabled()) {
                Set<Thread> allThreads = Thread.getAllStackTraces().keySet();
                LOGGER.debug("Alive threads ({}) overview:\n\t{}", allThreads.size(),
                        describeThreads(allThreads.stream().filter(Thread::isAlive)));
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public static String describeThreads(Collection<Thread> threads) {
        return describeThreads(threads.stream());
    }

    public static String describeThreads(Stream<Thread> threads) {
        return threads.map(AliveThreadReporter::describeThread).collect(Collectors.joining("; "));
    }

    public static String describeThread(Thread thread) {
        return thread.toString().replace("]", "") + "," + (thread.isDaemon() ? "daemon" : "") + "]";
    }
}
