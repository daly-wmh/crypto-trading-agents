package com.tradingagents.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.api.AnalysisService;
import com.tradingagents.api.dto.AnalysisRequest;
import com.tradingagents.api.dto.AnalysisResponse;
import com.tradingagents.core.event.GraphEvent;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    private final AnalysisService service;
    private final ObjectMapper mapper;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public AnalysisController(AnalysisService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public AnalysisResponse analyze(@Valid @RequestBody AnalysisRequest request) {
        return service.analyze(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@Valid @RequestBody AnalysisRequest request, HttpServletResponse response) {
        return stream(response, events -> service.analyzeWithEvents(request, events));
    }

    @PostMapping(value = "/resume/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@PathVariable Long id, HttpServletResponse response) {
        return stream(response, events -> service.resumeWithEvents(id, events));
    }

    private SseEmitter stream(HttpServletResponse response,
                              Function<Consumer<GraphEvent>, AnalysisResponse> action) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        SseEmitter emitter = new SseEmitter(900_000L);
        AtomicBoolean disconnected = new AtomicBoolean();
        emitter.onCompletion(() -> disconnected.set(true));

        workers.submit(() -> {
            try {
                var result = action.apply(event -> send(emitter, disconnected, event.type().name(), event));
                send(emitter, disconnected, "RESULT", result);
            } catch (RuntimeException error) {
                log.warn("Analysis stream failed", error);
                String reason = error instanceof ResponseStatusException status
                        ? status.getReason() : "分析失败。请查看失败的节点或检查模型服务。";
                var event = new GraphEvent(GraphEvent.EventType.GRAPH_ERROR, null, "分析失败",
                        reason, LocalDateTime.now());
                send(emitter, disconnected, "GRAPH_ERROR", event);
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, AtomicBoolean disconnected, String type, Object value) {
        if (disconnected.get()) return;
        try {
            emitter.send(SseEmitter.event().name(type).data(mapper.writeValueAsString(value), MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            disconnected.set(true);
        }
    }

    @PreDestroy
    void shutdown() {
        workers.shutdown();
    }
}
