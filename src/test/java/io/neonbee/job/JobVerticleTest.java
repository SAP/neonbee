package io.neonbee.job;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeProfile.NO_WEB;
import static io.neonbee.job.JobVerticle.FINALIZE_DELAY;
import static io.neonbee.test.base.NeonBeeTestBase.LONG_RUNNING_TEST;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.MockedStatic;

import io.neonbee.NeonBeeMockHelper;
import io.neonbee.NeonBeeOptions;
import io.neonbee.data.DataContext;
import io.neonbee.test.base.NeonBeeTestBase;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

@Tag(LONG_RUNNING_TEST)
class JobVerticleTest extends NeonBeeTestBase {
    private static class TestJobVerticle extends JobVerticle {
        public final Vertx vertxMock;

        public int jobExecuted;

        TestJobVerticle(JobSchedule schedule) {
            this(schedule, true /* undeploy when done */);
        }

        TestJobVerticle(JobSchedule schedule, boolean undeployWhenDone) {
            this(schedule, undeployWhenDone, false /* disable job scheduling */);
        }

        TestJobVerticle(JobSchedule schedule, boolean undeployWhenDone, boolean disableJobScheduling) {
            this(schedule, false /* do not sleep in execution */, 0 /* do not call the handler */, undeployWhenDone,
                    disableJobScheduling);
        }

        TestJobVerticle(JobSchedule schedule, boolean delayExecutions, int breakExecutionsAfter) {
            this(schedule, delayExecutions, breakExecutionsAfter, true /* undeploy when done */);
        }

        TestJobVerticle(JobSchedule schedule, boolean delayExecutions, int breakExecutionsAfter,
                boolean undeployWhenDone) {
            this(schedule, delayExecutions, breakExecutionsAfter, undeployWhenDone, false /* disable job scheduling */);
        }

        TestJobVerticle(JobSchedule schedule, boolean delayExecutions, int breakExecutionsAfter,
                boolean undeployWhenDone, boolean disableJobScheduling) {
            super(schedule, undeployWhenDone);
            NeonBeeMockHelper.registerNeonBeeMock(vertxMock = NeonBeeMockHelper.defaultVertxMock(),
                    defaultOptions().clearActiveProfiles().setDisableJobScheduling(disableJobScheduling));

            // calling setTimer on the mock should invoke the handler (once)
            doAnswer(invocation -> {
                // delay the execution for the sleep period
                if (delayExecutions) {
                    TimeUnit.MILLISECONDS.sleep(invocation.getArgument(0));
                }

                // limit to expected executions so we don't create a endless loop
                if (jobExecuted < breakExecutionsAfter) {
                    invocation.<Handler<Long>>getArgument(1).handle(1337L); // any timer ID
                }

                return null;
            }).when(vertxMock).setTimer(anyLong(), any());

            start(); // this should schedule the timer
        }

        @Override
        public Vertx getVertx() {
            return vertxMock;
        }

        @Override
        public Future<?> execute(DataContext context) {
            jobExecuted++;
            return Future.succeededFuture();
        }

        @Override
        public String deploymentID() {
            return "expected_deployment_id";
        }
    }

    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        options.addActiveProfile(NO_WEB);
        options.setDisableJobScheduling(false);
    }

    @Test
    @DisplayName("Verify that jobs are scheduled at the expected times")
    @SuppressWarnings("ReturnValueIgnored")
    void verifyJobSchedule() {

        Instant expectedInstant = Instant.parse("2023-01-01T00:00:00Z");
        ZonedDateTime zonedDateTime1 = ZonedDateTime.ofInstant(expectedInstant, ZoneOffset.UTC);

        try (
                MockedStatic<Instant> instantMock = mockStatic(Instant.class, CALLS_REAL_METHODS);
                MockedStatic<ZonedDateTime> zonedDateTimeMock = mockStatic(ZonedDateTime.class)) {

            instantMock.when(Instant::now).thenReturn(expectedInstant);
            zonedDateTimeMock.when(() -> ZonedDateTime.now(ZoneOffset.UTC)).thenReturn(zonedDateTime1);
            zonedDateTimeMock.when(() -> ZonedDateTime.ofInstant(any(), any(), any())).thenReturn(zonedDateTime1);

            TestJobVerticle testJobVerticle = new TestJobVerticle(new JobSchedule());
            // when a job is scheduled (once) the next time it is executed should be in approximately 100 ms (or less)
            verify(testJobVerticle.vertxMock).setTimer(eq(100L), any());

            testJobVerticle = new TestJobVerticle(new JobSchedule(Instant.now().minus(30, SECONDS)));
            // when a job is scheduled 30 seconds to the past, the job should not be scheduled
            verify(testJobVerticle.vertxMock, never()).setTimer(not(eq(FINALIZE_DELAY)), any());

            testJobVerticle = new TestJobVerticle(new JobSchedule(Instant.now().plus(30, SECONDS)));
            // when a job is scheduled 30 seconds to the future, the next job run should be scheduled at approximately
            // that time
            verify(testJobVerticle.vertxMock).setTimer(eq(30000L), any());

            /** PERIODIC JOBS **/

            testJobVerticle = new TestJobVerticle(
                    new JobSchedule(Instant.now().minus(30, SECONDS), Duration.ofMinutes(1)));
            // when a job is scheduled 30 seconds to the past, and the interval is one minute, the next execution should
            // be in 30 seconds
            verify(testJobVerticle.vertxMock).setTimer(eq(30000L), any());

            testJobVerticle = new TestJobVerticle(
                    new JobSchedule(Instant.now().minus(90, SECONDS), Duration.ofMinutes(1)));
            // when a job is scheduled 90 seconds to the past, and the interval is one minute, the next execution should
            // be in 30 seconds
            verify(testJobVerticle.vertxMock).setTimer(eq(30000L), any());

            testJobVerticle = new TestJobVerticle(
                    new JobSchedule(Instant.now().plus(30, SECONDS), Duration.ofMinutes(100)));
            // when a job is scheduled 30 seconds to the future, the next job run should be scheduled at approximately
            // that time (independent from the interval defined)
            verify(testJobVerticle.vertxMock).setTimer(eq(30000L), any());

            testJobVerticle = new TestJobVerticle(
                    new JobSchedule(Duration.ofMinutes(100), Instant.now().plus(30, SECONDS)));
            // when a job is scheduled and the end date is in the future, verify that a timer is set
            verify(testJobVerticle.vertxMock).setTimer(not(eq(FINALIZE_DELAY)), any());

            testJobVerticle = new TestJobVerticle(
                    new JobSchedule(Duration.ofMinutes(100), Instant.now().minus(30, SECONDS)), false, 1);
            // when a job is scheduled and the end date is in the past, verify that a timer is NOT set and the verticle
            // was undeployed
            verify(testJobVerticle.vertxMock, times(1)).setTimer(eq(FINALIZE_DELAY), any());
            verify(testJobVerticle.vertxMock).undeploy("expected_deployment_id");

            testJobVerticle = new TestJobVerticle(
                    new JobSchedule(Duration.ofMinutes(100), Instant.now().minus(30, SECONDS)), false);
            // when a job is scheduled and the end date is in the past, verify that a timer is NOT set and the verticle
            // was undeployed
            verify(testJobVerticle.vertxMock, never()).setTimer(not(eq(FINALIZE_DELAY)), any());
            verify(testJobVerticle.vertxMock, never()).undeploy(any());

            testJobVerticle = new TestJobVerticle(new JobSchedule(Instant.now().minus(30, SECONDS),
                    Duration.ofMinutes(1), Instant.now().plus(10, SECONDS)));
            // when a job is scheduled 30 seconds to the past, and the interval is one minute, but the job execution
            // should end 20 seconds to the future already, verify that a timer is NOT set
            verify(testJobVerticle.vertxMock, never()).setTimer(not(eq(FINALIZE_DELAY)), any());
        }
    }

    @Test
    @DisplayName("Verify that jobs executed when scheduled")
    @Disabled("This test is extremely influenced by available CPU time, which makes it very unstable on CI.")
    void verifyJobExecuted() {
        // if a one time job was scheduled, handler should be called once and undeploay should be called
        TestJobVerticle testJobVerticle = new TestJobVerticle(new JobSchedule(), false, 100);
        assertThat(testJobVerticle.jobExecuted).isEqualTo(1);
        verify(testJobVerticle.vertxMock).undeploy(any());

        // continuous job without an end (should be called the maximum number of times specified [100])
        testJobVerticle = new TestJobVerticle(new JobSchedule(Duration.ofMinutes(1)), false, 100);
        assertThat(testJobVerticle.jobExecuted).isEqualTo(100);
        verify(testJobVerticle.vertxMock, never()).undeploy(any());

        // continuous job with a start and without an end (should be called the maximum number of times specified
        // [100])
        testJobVerticle = new TestJobVerticle(
                new JobSchedule(Instant.now().plus(30, MINUTES), Duration.ofMinutes(1)), false, 100);
        assertThat(testJobVerticle.jobExecuted).isEqualTo(100);
        verify(testJobVerticle.vertxMock, never()).undeploy(any());

        // continuous job for 2 seconds, repeating every 500 milliseconds => the job should run 4 times and then
        // the verticle should get undeployed
        testJobVerticle = new TestJobVerticle(new JobSchedule(Duration.ofMillis(500),
                Instant.now().plus(2, SECONDS).minus(250, MILLIS)), true, 100);
        // 250 milliseconds are subtracted from the 2 seconds, because otherwise on very slow machines there is a
        // potential that a fifth run will be executed.
        assertThat(testJobVerticle.jobExecuted).isEqualTo(4);
        verify(testJobVerticle.vertxMock).undeploy(any());
    }

    @Test
    @DisplayName("Verify that jobs are disabled when flag is set")
    void verifyDisableJobScheduling() {
        TestJobVerticle testJobVerticle = new TestJobVerticle(new JobSchedule(), false, 1, true, true);
        assertThat(testJobVerticle.jobExecuted).isEqualTo(0);
        verify(testJobVerticle.vertxMock).undeploy(any());
    }

    @Test
    @DisplayName("Do not start JobVerticles with invalid JobSchedule")
    void testStartFailing(VertxTestContext testConetxt) {
        class DummyJobVerticle extends JobVerticle {

            DummyJobVerticle(JobSchedule schedule) {
                super(schedule);
            }

            @Override
            public Future<?> execute(DataContext context) {
                return Future.succeededFuture();
            }
        }

        DummyJobVerticle dummyJobVerticle = new DummyJobVerticle(new JobSchedule(Duration.ofMinutes(0)));
        deployVerticle(dummyJobVerticle).onComplete(testConetxt.failing(t -> {
            testConetxt.verify(() -> assertThat(t).hasMessageThat()
                    .isEqualTo("The period of a periodic JobSchedule can't be zero"));
            testConetxt.completeNow();
        }));
    }

    @Test
    @DisplayName("Do start JobVerticles with a valid JobSchedule")
    void testStartSucceeding(VertxTestContext testConetxt) {
        Checkpoint cp = testConetxt.checkpoint();
        class DummyJobVerticle extends JobVerticle {

            DummyJobVerticle(JobSchedule schedule) {
                super(schedule);
            }

            @Override
            public Future<?> execute(DataContext context) {
                cp.flag();
                return Future.succeededFuture();
            }
        }

        DummyJobVerticle dummyJobVerticle = new DummyJobVerticle(new JobSchedule(Duration.ofMinutes(1)));
        deployVerticle(dummyJobVerticle).onComplete(testConetxt.succeeding(v -> {}));
    }
}
