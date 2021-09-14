package io.neonbee;

import java.util.Optional;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NeonBeeTestExecutionListener} checks for any stale Vert.x threads after test execution. Generally after a
 * test finishes to execute it must clean up all Vert.x resources. If not this listener will print an error to the logs.
 */
public class NeonBeeTestExecutionListener implements TestExecutionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestExecutionListener.class);

    private boolean parallelExecution;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        parallelExecution = "true".equalsIgnoreCase(System.getProperty("junit.jupiter.execution.parallel.enabled"));
        if (parallelExecution) {
            LOGGER.warn("Cannot check for stale threads when running JUnit in parallel execution mode");
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!parallelExecution) {
            checkForStaleThreads("Vert.x", "vert.x-");
            checkForStaleThreads("Hazelcast", "hz.");
            checkForStaleThreads("WatchService", "FileSystemWatch");
        }
    }

    private static void checkForStaleThreads(String title, String namePrefix) {
        LOGGER.info("Checking for stale {} threads with '{}' prefix", title, namePrefix);
        Optional<Thread> staleThread = findStaleThread(namePrefix);
        if (staleThread.isPresent() && LOGGER.isErrorEnabled()) {
            LOGGER.error("Stale {} thread(s) detected!! Not closing the thread {} "
                    + "could result in the test runner not signaling completion", title, staleThread.get());
        }
    }

    private static Optional<Thread> findStaleThread(String namePrefix) {
        return Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().startsWith(namePrefix))
                .findAny();
    }
}
