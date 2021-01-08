package io.neonbee.job;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;

public class JobSchedule implements TemporalAdjuster {
    private Instant start;

    private Instant end;

    private TemporalAdjuster adjuster;

    /**
     * Create a job schedule for a one time job to run immediately.
     */
    public JobSchedule() {
        this((Instant) null /* start whenever the job is ready */);
    }

    /**
     * Create a job schedule for a job running once at a given instant.
     *
     * @param start when to start the job
     */
    public JobSchedule(Instant start) {
        this(start, (TemporalAdjuster) null /* not a periodic job */);
    }

    /**
     * Create a job schedule for a job running at a certain interval.
     *
     * @param interval a duration for the interval of the job
     */
    public JobSchedule(Duration interval) {
        this(null /* start whenever the job is ready */, interval);
    }

    /**
     * Create a job schedule for a job running at a certain interval, ending at a specific date.
     *
     * @param interval a duration for the interval of the job
     * @param end      any instant to end the job execution
     */
    public JobSchedule(Duration interval, Instant end) {
        this(null /* start whenever the job is ready */, interval, end);
    }

    /**
     * Create a job schedule for a job running at a certain interval, starting from a specific date.
     *
     * @param start    any instant to start the job execution
     * @param interval a duration for the interval of the job
     */
    public JobSchedule(Instant start, Duration interval) {
        this(start, interval, null /* never end the job */);
    }

    /**
     * Create a job schedule for a job running at a certain interval, starting from a specific date, ending at a
     * specific date.
     *
     * @param start    any instant to start the job execution
     * @param interval a duration for the interval of the job
     * @param end      any instant to end the job execution
     */
    public JobSchedule(Instant start, Duration interval, Instant end) {
        this(start, temporal -> temporal.plus(interval), end);
    }

    /**
     * Create a job schedule for a job running at a certain interval.
     *
     * @param adjuster a temporal adjuster to advance the last run date, to the next instant the job should run
     */
    public JobSchedule(TemporalAdjuster adjuster) {
        this(null /* start whenever the job is ready */, adjuster);
    }

    /**
     * Create a job schedule for a job running at a certain interval, ending at a specific date.
     *
     * @param adjuster a temporal adjuster to advance the last run date, to the next instant the job should run
     * @param end      any instant to end the job execution
     */
    public JobSchedule(TemporalAdjuster adjuster, Instant end) {
        this(null /* start whenever the job is ready */, adjuster, end);
    }

    /**
     * Create a job schedule for a job running at a certain interval, starting from a specific date.
     *
     * @param start    any instant to start the job execution
     * @param adjuster a temporal adjuster to advance the last run date, to the next instant the job should run
     */
    public JobSchedule(Instant start, TemporalAdjuster adjuster) {
        this(start, adjuster, null /* never end the job */);
    }

    /**
     * Create a job schedule for a job running at a certain interval, starting from a specific date, ending at a
     * specific date.
     *
     * @param start    any instant to start the job execution
     * @param adjuster a temporal adjuster to advance the last run date, to the next instant the job should run
     * @param end      any instant to end the job execution
     */
    public JobSchedule(Instant start, TemporalAdjuster adjuster, Instant end) {
        this.start = start;
        this.adjuster = adjuster;
        this.end = end;
    }

    /**
     * Returns the start instant of this schedule, or null in case the job should be scheduled immediately.
     *
     * @return the start instant of the schedule
     */
    public Instant getStart() {
        return start;
    }

    /**
     * Returns the end instant of the schedule, or null inc ase the job schedule should never end, or this is not a
     * periodic job schedule.
     *
     * @return the end instant of the schedule
     */
    public Instant getEnd() {
        return end;
    }

    /**
     * Returns whether the schedule is repeated periodically.
     *
     * @return whether the schedule is repeated periodically
     */
    public boolean isPeriodic() {
        return adjuster != null;
    }

    /**
     * Returns a temporal adjuster to advance the periodic.
     *
     * @return a temporal adjuster to advance the periodic
     */
    public TemporalAdjuster getAdjuster() {
        return adjuster;
    }

    /**
     * Convenience implementation of the {@link TemporalAdjuster} interface.
     * <p>
     * This allows to call <code>schedule.adjustInto</code> instead of having to call
     * <code>schedule.getAdjuster().adjustInto</code> for periodic schedules.
     */
    @Override
    public Temporal adjustInto(Temporal temporal) {
        return adjuster.adjustInto(temporal);
    }
}
