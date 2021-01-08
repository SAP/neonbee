package io.neonbee.job;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class JobScheduleTest {
    @Test
    @DisplayName("Initialize JobSchedule")
    public void testJobSchedule() {
        Instant now = Instant.now();

        JobSchedule schedule = new JobSchedule();
        assertThat(schedule.getStart()).isNull();
        assertThat(schedule.getEnd()).isNull();
        assertThat(schedule.isPeriodic()).isFalse();
        assertThat(schedule.getAdjuster()).isNull();

        schedule = new JobSchedule(now);
        assertThat(schedule.getStart()).isEqualTo(now);
        assertThat(schedule.getEnd()).isNull();
        assertThat(schedule.isPeriodic()).isFalse();
        assertThat(schedule.getAdjuster()).isNull();

        schedule = new JobSchedule(Duration.ofHours(1));
        assertThat(schedule.getStart()).isNull();
        assertThat(schedule.getEnd()).isNull();
        assertThat(schedule.isPeriodic()).isTrue();
        assertThat(now.until(schedule.adjustInto(now), ChronoUnit.MINUTES)).isEqualTo(60);

        schedule = new JobSchedule(now, Duration.ofMinutes(1337), now);
        assertThat(schedule.getStart()).isEqualTo(now);
        assertThat(schedule.getEnd()).isEqualTo(now);
        assertThat(schedule.isPeriodic()).isTrue();
        assertThat(now.until(schedule.adjustInto(now), ChronoUnit.MINUTES)).isEqualTo(1337);

        schedule = new JobSchedule(now, temporal -> {
            return temporal.plus(20, ChronoUnit.SECONDS);
        }, now);
        assertThat(now.until(schedule.adjustInto(now), ChronoUnit.SECONDS)).isEqualTo(20);
    }
}
