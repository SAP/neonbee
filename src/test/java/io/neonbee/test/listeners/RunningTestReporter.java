package io.neonbee.test.listeners;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("checkstyle:MultipleVariableDeclarations")
public class RunningTestReporter implements TestExecutionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestExecutionListener.class);

    public final Set<TestIdentifier> expectedTests = Collections.synchronizedSet(new HashSet<>()),
            runningTests = Collections.synchronizedSet(new HashSet<>()),
            skippedTests = Collections.synchronizedSet(new HashSet<>()),
            finishedTests = Collections.synchronizedSet(new HashSet<>());

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testPlanExecutionStarted(TestPlan testPlan) {
        collectTests(testPlan, testPlan.getRoots());

        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "neonbee-running-test-reporter");
            thread.setDaemon(true);
            return thread;
        }).scheduleAtFixedRate(() -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Running test overview:\n\tExpected ({}): {}\n\tRunning ({}): {}\n\tSkipped ({}): {}\n\tFinished ({}): {}",
                        expectedTests.size(), describeTests(expectedTests), runningTests.size(),
                        describeTests(runningTests), skippedTests.size(), describeTests(skippedTests),
                        finishedTests.size(), describeTests(finishedTests));
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void collectTests(TestPlan testPlan, Set<TestIdentifier> identifiers) {
        identifiers.forEach(identifier -> {
            expectedTests.add(identifier);
            if (identifier.isContainer()) {
                collectTests(testPlan, testPlan.getChildren(identifier));
            }
        });
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier) {
        expectedTests.add(testIdentifier);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (expectedTests.remove(testIdentifier)) {
            LOGGER.debug("Execution of test {} started", describeTest(testIdentifier));
            runningTests.add(testIdentifier);
        } else {
            LOGGER.error("Stumbled over test {} to execute that was not expected!", describeTest(testIdentifier));
        }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (expectedTests.remove(testIdentifier)) {
            LOGGER.debug("Execution of test {} skipped", describeTest(testIdentifier));
            skippedTests.add(testIdentifier);
        } else {
            LOGGER.error("Stumbled over test {} to skip that was not expected!", describeTest(testIdentifier));
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (runningTests.remove(testIdentifier)) {
            LOGGER.debug("Execution of test {} finished", describeTest(testIdentifier));
            finishedTests.add(testIdentifier);
        } else {
            LOGGER.error("Stumbled over test {} to finish that was not started!", describeTest(testIdentifier));
        }
    }

    public static String describeTests(Collection<TestIdentifier> identifiers) {
        return describeTests(identifiers.stream());
    }

    public static String describeTests(Stream<TestIdentifier> identifiers) {
        return identifiers.map(RunningTestReporter::describeTest).collect(Collectors.joining("; "));
    }

    public static String describeTest(TestIdentifier identifier) {
        return identifier.getDisplayName().replaceFirst("(?<=.{32}).+", "...");
    }
}
