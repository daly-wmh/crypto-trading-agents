package com.tradingagents.api.controller;

import com.tradingagents.scheduler.ReflectionSchedule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/portfolio-reflection")
public class ReflectionScheduleController {

    private final ReflectionSchedule schedule;

    public ReflectionScheduleController(ReflectionSchedule schedule) {
        this.schedule = schedule;
    }

    @GetMapping
    public ReflectionSchedule.View get() {
        return schedule.view();
    }

    @PutMapping
    public ReflectionSchedule.View update(@Valid @RequestBody Update request) {
        return schedule.update(request.enabled(), request.cron());
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReflectionSchedule.View run() {
        return schedule.runNow();
    }

    public record Update(@NotNull Boolean enabled, @NotBlank @Size(max = 120) String cron) {}
}
