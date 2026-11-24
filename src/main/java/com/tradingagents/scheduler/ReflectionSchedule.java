package com.tradingagents.scheduler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

@Service
@DependsOnDatabaseInitialization
public class ReflectionSchedule {

    private static final Logger log = LoggerFactory.getLogger(ReflectionSchedule.class);
    private static final String DAILY_CRON = "0 0 1 * * *";
    private static final String UNAVAILABLE = "启用 PgVector 后可运行持仓复盘";

    private final JdbcTemplate jdbc;
    private final TaskScheduler scheduler;
    private final PortfolioReflectionService portfolio;
    private final Settings defaults;

    private Settings settings;
    private Instant nextRunAt;
    private ScheduledFuture<?> scheduled;
    private long revision;
    private UUID activeRun;
    private CompletedRun completedRun;
    private boolean closed;

    public ReflectionSchedule(JdbcTemplate jdbc, TaskScheduler scheduler,
                              ObjectProvider<PortfolioReflectionService> portfolio,
                              @Value("${crypto.reflection.cron}") String configuredCron) {
        this.jdbc = jdbc;
        this.scheduler = scheduler;
        this.portfolio = portfolio.getIfAvailable();
        boolean enabled = !configuredCron.strip().equals("-");
        this.defaults = new Settings(enabled, enabled ? configuredCron.strip() : DAILY_CRON);
    }

    @PostConstruct
    synchronized void initialize() {
        settings = jdbc.queryForObject("SELECT enabled, cron FROM reflection_schedule WHERE id = 1",
                (row, index) -> row.getString("cron") == null
                        ? defaults : new Settings(row.getBoolean("enabled"), row.getString("cron")));
        jdbc.update("""
                UPDATE reflection_run SET status = 'INTERRUPTED', finished_at = ?, summary = ?
                WHERE status = 'RUNNING'
                """, now(), "服务重启，复盘中断");
        scheduleNext(nextTime(settings));
    }

    public synchronized View view() {
        flushCompletion();
        List<Run> runs = jdbc.query("""
                SELECT id, trigger_type, status, started_at, finished_at, summary
                FROM reflection_run ORDER BY started_at DESC, id DESC LIMIT 30
                """, (row, index) -> new Run(row.getObject("id", UUID.class),
                row.getString("trigger_type"), row.getString("status"),
                row.getObject("started_at", OffsetDateTime.class),
                row.getObject("finished_at", OffsetDateTime.class), row.getString("summary")));
        return new View(portfolio != null, portfolio == null ? UNAVAILABLE : null, settings.enabled(),
                settings.cron(), nextRunAt, activeRun != null, runs);
    }

    public synchronized View update(boolean enabled, String cron) {
        String expression = cron.strip();
        try {
            if (CronExpression.parse(expression).next(now()) == null) {
                throw new IllegalArgumentException("No matching time");
            }
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的 Spring Cron 表达式");
        }
        Settings replacement = new Settings(enabled, expression);
        Instant next = nextTime(replacement);
        jdbc.update("UPDATE reflection_schedule SET enabled = ?, cron = ? WHERE id = 1", enabled, expression);
        scheduleNext(next);
        settings = replacement;
        return view();
    }

    public synchronized View runNow() {
        if (portfolio == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, UNAVAILABLE);
        }
        flushCompletion();
        if (activeRun != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "持仓复盘正在运行");
        }
        submit("MANUAL");
        return view();
    }

    private Instant nextTime(Settings current) {
        if (!current.enabled() || portfolio == null) return null;
        OffsetDateTime next = CronExpression.parse(current.cron()).next(now());
        return next == null ? null : next.toInstant();
    }

    private void scheduleNext(Instant next) {
        long nextRevision = revision + 1;
        ScheduledFuture<?> replacement = next == null ? null
                : scheduler.schedule(() -> runScheduled(nextRevision), next);
        if (scheduled != null) scheduled.cancel(false);
        scheduled = replacement;
        revision = nextRevision;
        nextRunAt = next;
    }

    private synchronized void runScheduled(long expectedRevision) {
        if (closed || revision != expectedRevision) return;
        scheduled = null;
        scheduleNext(nextTime(settings));
        flushCompletion();
        if (activeRun == null) submit("SCHEDULED");
    }

    private void submit(String trigger) {
        if (closed) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "服务正在关闭");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reflection_run (id, trigger_type, status, started_at, summary)
                VALUES (?, ?, 'RUNNING', ?, ?)
                """, id, trigger, now(), "正在评估持仓");
        activeRun = id;
        try {
            scheduler.schedule(() -> execute(id), scheduler.getClock().instant());
        } catch (RejectedExecutionException error) {
            finish(id, "FAILED", "服务暂时无法启动复盘");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "服务暂时无法启动复盘");
        }
    }

    private void execute(UUID id) {
        Completion completion = new Completion("FAILED", "复盘异常，请查看后端日志");
        try {
            var result = portfolio.evaluate();
            if (result.evaluated() == 0 && result.failed() == 0) {
                completion = new Completion("SKIPPED", result.skipped() == 0
                        ? "暂无模拟持仓" : "%d 个持仓缺少可复盘的研报".formatted(result.skipped()));
            } else {
                String status = result.failed() == 0 ? "SUCCESS"
                        : result.evaluated() == 0 ? "FAILED" : "PARTIAL_FAILURE";
                String summary = "已评估 %d，写入经验 %d，跳过 %d，失败 %d"
                        .formatted(result.evaluated(), result.stored(), result.skipped(), result.failed());
                completion = new Completion(status, summary);
            }
        } catch (RuntimeException error) {
            log.error("持仓复盘执行失败，运行 ID {}", id, error);
        } finally {
            finish(id, completion.status(), completion.summary());
        }
    }

    private synchronized void finish(UUID id, String status, String summary) {
        completedRun = new CompletedRun(id, status, summary, now());
        try {
            flushCompletion();
        } catch (DataAccessException error) {
            log.error("暂时无法保存复盘结果，运行 ID {}", id, error);
        }
    }

    private void flushCompletion() {
        if (completedRun == null) return;
        jdbc.update("""
                UPDATE reflection_run SET status = ?, finished_at = ?, summary = ? WHERE id = ?
                """, completedRun.status(), completedRun.finishedAt(), completedRun.summary(), completedRun.id());
        completedRun = null;
        activeRun = null;
    }

    private OffsetDateTime now() {
        return scheduler.getClock().instant().atOffset(ZoneOffset.UTC);
    }

    @PreDestroy
    synchronized void stop() {
        closed = true;
        if (scheduled != null) scheduled.cancel(false);
    }

    private record Settings(boolean enabled, String cron) {}

    private record Completion(String status, String summary) {}

    private record CompletedRun(UUID id, String status, String summary, OffsetDateTime finishedAt) {}

    public record View(boolean available, String unavailableReason, boolean enabled, String cron,
                       Instant nextRunAt, boolean running, List<Run> runs) {}

    public record Run(UUID id, String trigger, String status, OffsetDateTime startedAt,
                      OffsetDateTime finishedAt, String summary) {}
}
