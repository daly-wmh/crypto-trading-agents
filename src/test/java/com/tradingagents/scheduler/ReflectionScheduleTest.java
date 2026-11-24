package com.tradingagents.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReflectionScheduleTest {

    private static final String DEFAULT_CRON = "0 0 1 * * *";
    private static final String EDITED_CRON = "0 0 2 * * *";
    private static final Instant START = Instant.parse("2026-11-23T04:00:00Z");

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final PortfolioReflectionService portfolio = mock(PortfolioReflectionService.class);
    private final List<Scheduled> callbacks = new ArrayList<>();
    private EmbeddedDatabase database;

    @BeforeEach
    void setup() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2)
                .addScript("db/migration/V3__reflection_schedule.sql").build();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            callbacks.add(new Scheduled(call.getArgument(0), future));
            return future;
        });
        moveClock(START);
    }

    @AfterEach
    void shutdown() {
        database.shutdown();
    }

    @Test
    void reschedulesAndSharesOneExecutionSlotAcrossTriggers() {
        ReflectionSchedule schedule = create(DEFAULT_CRON);
        assertThat(schedule.view().nextRunAt()).isEqualTo("2026-11-24T01:00:00Z");

        var updated = schedule.update(true, EDITED_CRON);
        assertThat(updated.nextRunAt()).isEqualTo("2026-11-24T02:00:00Z");
        verify(callbacks.get(0).future()).cancel(false);
        callbacks.get(0).task().run();
        assertThat(schedule.view().runs()).isEmpty();

        var manual = schedule.runNow();
        assertThat(manual.running()).isTrue();
        assertThat(manual.runs().getFirst().trigger()).isEqualTo("MANUAL");
        assertThatThrownBy(schedule::runNow).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        moveClock(updated.nextRunAt());
        callbacks.get(1).task().run();
        assertThat(schedule.view().nextRunAt()).isEqualTo("2026-11-25T02:00:00Z");
        assertThat(schedule.view().runs()).hasSize(1);

        when(portfolio.evaluate()).thenReturn(new PortfolioReflectionService.Result(1, 1, 1, 1),
                new PortfolioReflectionService.Result(0, 0, 0, 0));
        callbacks.get(2).task().run();
        var completed = schedule.view();
        assertThat(completed.running()).isFalse();
        assertThat(completed.runs().getFirst().status()).isEqualTo("PARTIAL_FAILURE");
        assertThat(completed.runs().getFirst().finishedAt()).isNotNull();

        moveClock(completed.nextRunAt());
        callbacks.get(3).task().run();
        assertThat(schedule.view().runs().getFirst().trigger()).isEqualTo("SCHEDULED");
        callbacks.get(5).task().run();
        assertThat(schedule.view().runs().getFirst().status()).isEqualTo("SKIPPED");
    }

    @Test
    void restoresPausedPlanAndMarksUnfinishedRunAfterRestart() {
        ReflectionSchedule schedule = create("-");
        assertThat(schedule.view().enabled()).isFalse();
        assertThat(schedule.view().nextRunAt()).isNull();
        assertThatThrownBy(() -> schedule.update(true, "0 0 0 31 2 *"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        schedule.update(false, EDITED_CRON);
        var started = schedule.runNow().runs().getFirst();
        schedule.stop();

        ReflectionSchedule restarted = create(DEFAULT_CRON);
        var restored = restarted.view();
        assertThat(restored.enabled()).isFalse();
        assertThat(restored.cron()).isEqualTo(EDITED_CRON);
        assertThat(restored.nextRunAt()).isNull();
        assertThat(restored.running()).isFalse();
        var interrupted = restored.runs().getFirst();
        assertThat(interrupted.id()).isEqualTo(started.id());
        assertThat(interrupted.status()).isEqualTo("INTERRUPTED");
    }

    private ReflectionSchedule create(String configuredCron) {
        var factory = new StaticListableBeanFactory();
        factory.addBean("portfolio", portfolio);
        var schedule = new ReflectionSchedule(new JdbcTemplate(database), scheduler,
                factory.getBeanProvider(PortfolioReflectionService.class), configuredCron);
        schedule.initialize();
        return schedule;
    }

    private void moveClock(Instant clock) {
        when(scheduler.getClock()).thenReturn(Clock.fixed(clock, ZoneOffset.UTC));
    }

    private record Scheduled(Runnable task, ScheduledFuture<?> future) {}
}
