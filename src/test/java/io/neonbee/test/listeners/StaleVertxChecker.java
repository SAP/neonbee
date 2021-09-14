package io.neonbee.test.listeners;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxThread;

/**
 * The {@link StaleVertxChecker} checks for any stale Vert.x threads after test execution. Generally after a test
 * finishes to execute it must clean up all Vert.x resources. If not this listener will print an error to the logs.
 */
public class StaleVertxChecker extends StaleThreadChecker {
    public static final SetMultimap<Vertx, String> VERTX_TEST_MAP = HashMultimap.create();

    private static final Logger LOGGER = LoggerFactory.getLogger(StaleVertxChecker.class);

    private static final Method CONTEXT_METHOD;

    static {
        Method contextMethod = null;
        try {
            (contextMethod = VertxThread.class.getDeclaredMethod("context")).setAccessible(true);
        } catch (NoSuchMethodException | SecurityException e) {
            LOGGER.warn("Cannot set context method of VertxThread accessible, checking for stale threads is limited");
        } finally {
            CONTEXT_METHOD = contextMethod;
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        super.executionFinished(testIdentifier, testExecutionResult);
        if (CONTEXT_METHOD != null) {
            checkForStaleVertxInstances(testIdentifier);
        }
    }

    @SuppressWarnings({ "PMD.EmptyIfStmt", "checkstyle:MultipleVariableDeclarations" })
    private void checkForStaleVertxInstances(TestIdentifier testIdentifier) {
        LOGGER.info("Checking for stale Vert.x instances");

        // first try to determine all Vert.x instances that are currently available (and running?)
        Set<Vertx> vertxInstances = findStaleThreads(VERTX_THREAD_NAME_PREFIX).filter(VertxThread.class::isInstance)
                .map(VertxThread.class::cast).map(thread -> {
                    try {
                        Context context = (Context) CONTEXT_METHOD.invoke(thread);
                        if (context == null) {
                            LOGGER.debug("Vert.x thread {} is current not associated to any context", thread);
                            return null;
                        }

                        Vertx vertx = context.owner();
                        if (vertx == null) {
                            LOGGER.debug("Vert.x thread {} has a context {} with no owner, is this a bug?!", context,
                                    thread);
                            return null;
                        }

                        return vertx;
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        LOGGER.debug("Failed to determine Vert.x context for thread {}", thread);
                        return null;
                    }
                }).collect(Collectors.toSet());

        if (vertxInstances.isEmpty()) {
            // perfect! we don't need to bother ourself!
            LOGGER.info("No stale Vert.x instances found");
            return;
        } else if (vertxInstances.contains(null)) {
            LOGGER.warn("Could not determine Vert.x instance for all Vert.x threads");
        }

        // try to find instances associated to this test or any instances that we cannot associate to any test
        Set<Vertx> unassociatedVertxInstances = new HashSet<>(), associatedVertxInstances = new HashSet<>(),
                notAssociatedVertxInstances = new HashSet<>();
        vertxInstances.stream().filter(Objects::nonNull).forEach(vertx -> {
            Set<String> associatedToTests = VERTX_TEST_MAP.get(vertx);
            if (associatedToTests.isEmpty()) {
                // this Vert.x instance was never associated to any test, thus we don't know if it is ours
                unassociatedVertxInstances.add(vertx);
            } else if (associatedToTests.contains(testIdentifier.getDisplayName())) {
                // unfortunately the display name is the only "identifier" shared between TestInfo and TestIdentifier
                associatedVertxInstances.add(vertx);
            } else {
                // this Vert.x instance is owned by a test, but it's not us! phew...
                notAssociatedVertxInstances.add(vertx);
            }
        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Vert.x instance overview:\n\nUnassociated: {}\nAssociated: {}\nNot Associated: {}",
                    unassociatedVertxInstances, associatedVertxInstances, notAssociatedVertxInstances);
        }

        // in case there are any associated Vert.x instances running, log them out to console
        if (!associatedVertxInstances.isEmpty()) {
            logStaleVertxInstances(associatedVertxInstances);
        } else if (!parallelExecution && (!unassociatedVertxInstances.isEmpty() || vertxInstances.contains(null))) {
            // we can only make sense of this, in case we are NOT in parallel execution, if we find a unassociated
            // instance or an instance that we cannot determine the Vert.x instance for in parallel execution mode it
            // could likely be from another test, that is currently being executed, thus better don't log anything.
            // however if we are NOT in parallel execution, the first test which this log message appears leaks:
            if (!unassociatedVertxInstances.isEmpty()) {
                logStaleVertxInstances(unassociatedVertxInstances);
            } else if (vertxInstances.contains(null)) {
                // there are Vert.x threads, that we were unable to determine the Vert.x instance for, do not deal with
                // this here, because we have the StaleThreadChecker backing us up in such cases
            }
        }
    }

    private void logStaleVertxInstances(Collection<Vertx> vertxInstances) {
        Optional<Vertx> notRunningVertx =
                vertxInstances.stream().filter(Predicate.not(StaleVertxChecker::probeVertxRunning)).findAny();
        if (!notRunningVertx.isPresent()) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(
                        "Stale and running Vert.x instance found!! Not closing Vert.x instance {} "
                                + "could result in the test runner not signaling completion",
                        vertxInstances.stream().findAny());
            }
        } else {
            LOGGER.error("Stale closed (!) Vert.x instance {} with running threads found!! This is a bug!",
                    notRunningVertx.get());
        }
    }

    private static boolean probeVertxRunning(Vertx vertx) {
        try {
            vertx.deployVerticle(() -> (Verticle) null, new DeploymentOptions().setInstances(0));
            return false; // if it is not running the deployVerticles call will immediately return with a failed future
        } catch (IllegalArgumentException e) {
            // we do probe for an illegal argument exception, as if Vert.x is closed, the deployVerticle call will
            // actually return a failed future instead, if it is not closed however, the DeploymentManager throws
            // the exception instead, which indicates to us, Vert.x was not closed properly!
            return true;
        }
    }
}
