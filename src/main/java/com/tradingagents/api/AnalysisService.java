package com.tradingagents.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.api.dto.AnalysisRequest;
import com.tradingagents.api.dto.AnalysisResponse;
import com.tradingagents.api.entity.AnalysisReport;
import com.tradingagents.api.repository.AnalysisReportRepository;
import com.tradingagents.core.event.GraphEvent;
import com.tradingagents.core.graph.AgentGraph;
import com.tradingagents.core.graph.GraphExecutor;
import com.tradingagents.core.graph.PipelineService;
import com.tradingagents.core.state.AgentState;
import com.tradingagents.market.CryptoMarketService;
import com.tradingagents.memory.AgentMemoryService;
import com.tradingagents.trading.dto.PaperSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final PipelineService pipeline;
    private final CryptoMarketService market;
    private final AnalysisReportRepository reports;
    private final AgentMemoryService memory;
    private final ObjectMapper mapper;

    public AnalysisService(PipelineService pipeline, CryptoMarketService market,
                           AnalysisReportRepository reports, AgentMemoryService memory,
                           ObjectMapper mapper) {
        this.pipeline = pipeline;
        this.market = market;
        this.reports = reports;
        this.memory = memory;
        this.mapper = mapper;
    }

    public AnalysisResponse analyze(AnalysisRequest request) {
        return analyzeWithEvents(request, null);
    }

    public AnalysisResponse analyzeWithEvents(AnalysisRequest request, Consumer<GraphEvent> events) {
        String pair = market.requirePair(request.stockCode());
        AnalysisReport report = new AnalysisReport();
        report.setStockCode(pair);
        report.setStockName(market.displayName(pair));
        report.setStatus("RUNNING");
        report.setStateSnapshot(write(new AnalysisCheckpoint(request.getKlineDaysOrDefault(), request.enabledAgents(),
                null, null, null, List.of(), List.of(), Map.of(), 0, List.of())));
        reports.save(report);
        return run(report, events);
    }

    public AnalysisResponse resumeWithEvents(Long reportId, Consumer<GraphEvent> events) {
        if (reports.claimForResume(reportId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该报告当前无法续跑");
        }
        AnalysisReport report = reports.findById(reportId).orElseThrow();
        return run(report, events);
    }

    private AnalysisResponse run(AnalysisReport report, Consumer<GraphEvent> events) {
        long start = System.currentTimeMillis();
        try {
            if (events != null) {
                events.accept(GraphEvent.reportCreated(report.getStockCode(), report.getId()));
            }

            AgentGraph graph = pipeline.buildGraph();
            AnalysisCheckpoint checkpoint = mapper.readValue(report.getStateSnapshot(), AnalysisCheckpoint.class);
            int nextStage = resumeIndex(graph, report.getStage());
            List<NodeReport> nodeReports = Collections.synchronizedList(new ArrayList<>());
            if (nextStage > 0 && checkpoint.nodeReports() != null) nodeReports.addAll(checkpoint.nodeReports());
            if (events != null) {
                var stages = graph.getStages().stream().map(stage -> new StagePlan(
                        stage.getId(), String.join(" · ", stage.getNodes().stream().map(node -> node.getDisplayName()).toList()),
                        stage.getNodes().size() * stage.getMaxRounds(),
                        stage.getNodes().stream().map(node -> node.getId()).toList())).toList();
                events.accept(GraphEvent.pipelinePlan(write(new PipelinePlan(stages, nextStage, List.copyOf(nodeReports)))));
            }
            AgentState state = restoreState(report, checkpoint, nextStage > 0, events);
            GraphExecutor executor = new GraphExecutor(graph, event -> {
                if (event.type() == GraphEvent.EventType.NODE_COMPLETE && !"[已跳过]".equals(event.data())) {
                    nodeReports.add(new NodeReport(event.stageId(), event.nodeId(), event.nodeName(), event.data()));
                }
                if (events != null) events.accept(event);
            });
            executor.execute(state, nextStage, (index, stageId) -> {
                copyReports(report, state);
                report.setStage(stageId);
                report.setStateSnapshot(write(checkpointFrom(state, checkpoint.candleCount(), nodeReports)));
                reports.save(report);
            });

            if (state.getFinalDecision() == null || state.getFinalDecision().isBlank()) {
                throw new IllegalStateException("流水线需要启用最终研报节点");
            }

            long duration = System.currentTimeMillis() - start;
            copyReports(report, state);
            report.setStatus("COMPLETED");
            report.setDurationMs(duration);
            report.setStateSnapshot(null);
            reports.save(report);
            storeMemory(state);
            return AnalysisResponse.from(state, duration, report.getId());
        } catch (Exception e) {
            report.setStatus("FAILED");
            report.setDurationMs(System.currentTimeMillis() - start);
            reports.save(report);
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("无法恢复分析状态", e);
        }
    }

    private AgentState restoreState(AnalysisReport report, AnalysisCheckpoint saved, boolean resume,
                                    Consumer<GraphEvent> events) {
        AgentState state = new AgentState(report.getStockCode());
        state.setStockName(report.getStockName());
        state.setEnabledAgents(saved.enabledAgents());
        if (resume) {
            state.setMarketReport(report.getMarketReport());
            state.setNewsReport(report.getNewsReport());
            state.setSocialReport(report.getSocialReport());
            state.setFundamentalReport(report.getFundamentalReport());
            state.setResearchDecision(report.getResearchDecision());
            state.setTradeDecision(report.getTradeDecision());
            state.setFinalDecision(report.getFinalDecision());
            state.setRecalledMemories(report.getRecalledMemories());
            state.getBullArguments().addAll(saved.bullArguments());
            state.getBearArguments().addAll(saved.bearArguments());
            state.getNodeTimings().putAll(saved.nodeTimings());
            state.addTokens(saved.totalTokens());
            state.putContext("kline_context", saved.klines());
            state.putContext("technical_indicators", saved.indicators());
            state.putContext("ticker_context", saved.ticker());
            return state;
        }

        String code = report.getStockCode();
        String klines = market.buildMultiTimeframeContext(code, saved.candleCount());
        String indicators = market.buildIndicatorSummary(code, saved.candleCount());
        var ticker = market.ticker(code);
        String tickerContext = """
                Binance 现货滚动 24 小时 (%s，UTC)
                最新价 %s USDT，涨跌幅 %s%%，区间 %s 至 %s USDT，成交额 %s USDT。
                """.formatted(ticker.observedAt(), ticker.price(), ticker.changePercent(),
                ticker.low(), ticker.high(), ticker.quoteVolume());
        state.putContext("kline_context", klines);
        state.putContext("technical_indicators", indicators);
        state.putContext("ticker_context", tickerContext);

        String memories = memory.recallRelevantMemories(code, code + "\n" + indicators + tickerContext, 4);
        if (memories != null && !memories.isBlank()) {
            state.setRecalledMemories(memories);
            if (events != null) events.accept(GraphEvent.memoryRecall(code, memories));
        }
        return state;
    }

    private static int resumeIndex(AgentGraph graph, String lastStage) {
        var stages = graph.getStages();
        for (int index = 0; index < stages.size(); index++) {
            if (Objects.equals(stages.get(index).getId(), lastStage)) return index + 1;
        }
        return 0;
    }

    private static AnalysisCheckpoint checkpointFrom(AgentState state, int count, List<NodeReport> nodeReports) {
        return new AnalysisCheckpoint(count, state.getEnabledAgents(), state.getContext("kline_context"),
                state.getContext("technical_indicators"), state.getContext("ticker_context"),
                List.copyOf(state.getBullArguments()), List.copyOf(state.getBearArguments()),
                Map.copyOf(state.getNodeTimings()), state.getTotalTokens(), List.copyOf(nodeReports));
    }

    private static void copyReports(AnalysisReport report, AgentState state) {
        report.setMarketReport(state.getMarketReport());
        report.setNewsReport(state.getNewsReport());
        report.setSocialReport(state.getSocialReport());
        report.setFundamentalReport(state.getFundamentalReport());
        report.setBullArguments(state.getDebateHistory());
        report.setResearchDecision(state.getResearchDecision());
        report.setTradeDecision(state.getTradeDecision());
        report.setFinalDecision(state.getFinalDecision());
        report.setRecalledMemories(state.getRecalledMemories());
        report.setTotalTokens(state.getTotalTokens());
    }

    private void storeMemory(AgentState state) {
        try {
            String decision = state.getFinalDecision();
            String direction = PaperSignal.from(decision).map(signal -> signal.action().name()).orElse("UNKNOWN");
            String marketContext = state.<String>getContext("technical_indicators") + state.<String>getContext("ticker_context");
            memory.storeSituationMemory(state.getStockCode(), state.getStockName(), marketContext,
                    direction, decision.substring(0, Math.min(decision.length(), 500)));
        } catch (Exception e) {
            log.warn("Could not store analysis memory for {}", state.getStockCode(), e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存分析状态", e);
        }
    }

    private record AnalysisCheckpoint(int candleCount, List<String> enabledAgents, String klines, String indicators,
                                      String ticker, List<String> bullArguments, List<String> bearArguments,
                                      Map<String, Long> nodeTimings, int totalTokens, List<NodeReport> nodeReports) {}

    private record StagePlan(String id, String name, int workUnits, List<String> nodeIds) {}

    private record PipelinePlan(List<StagePlan> stages, int completedStages, List<NodeReport> nodeReports) {}

    private record NodeReport(String stageId, String nodeId, String nodeName, String content) {}
}
