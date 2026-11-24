package com.tradingagents.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.api.entity.AnalysisReport;
import com.tradingagents.api.repository.AnalysisReportRepository;
import com.tradingagents.market.CryptoMarketService;
import com.tradingagents.memory.AgentMemoryService;
import com.tradingagents.model.ModelClientResolver;
import com.tradingagents.trading.dto.PositionVO;
import com.tradingagents.trading.service.TradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "pgvector")
@Service
public class PortfolioReflectionService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioReflectionService.class);

    private final TradingService tradingService;
    private final AnalysisReportRepository reportRepository;
    private final CryptoMarketService marketDataService;
    private final AgentMemoryService memoryService;
    private final ModelClientResolver modelClientResolver;
    private final ObjectMapper mapper;

    public PortfolioReflectionService(TradingService tradingService,
                                      AnalysisReportRepository reportRepository,
                                      CryptoMarketService marketDataService,
                                      AgentMemoryService memoryService,
                                      ModelClientResolver modelClientResolver,
                                      ObjectMapper mapper) {
        this.tradingService = tradingService;
        this.reportRepository = reportRepository;
        this.marketDataService = marketDataService;
        this.memoryService = memoryService;
        this.modelClientResolver = modelClientResolver;
        this.mapper = mapper;
    }

    Result evaluate() {
        var positions = tradingService.getAccountSummary(1L).positions();
        int evaluated = 0;
        int skipped = 0;
        int failed = 0;
        int stored = 0;
        for (PositionVO position : positions) {
            try {
                switch (evaluatePosition(position)) {
                    case SKIPPED -> skipped++;
                    case EVALUATED -> evaluated++;
                    case STORED -> {
                        evaluated++;
                        stored++;
                    }
                }
            } catch (Exception error) {
                failed++;
                log.error("评估 {} 失败", position.stockCode(), error);
            }
        }
        return new Result(evaluated, skipped, failed, stored);
    }

    private Evaluation evaluatePosition(PositionVO pos) throws JsonProcessingException {
        String code = pos.stockCode();
        log.info("评估持仓: {}", code);

        // Find most recent analysis report for this stock
        List<AnalysisReport> reports = reportRepository.findByStockCodeOrderByCreatedAtDesc(code);
        AnalysisReport latestReport = reports.isEmpty() ? null : reports.get(0);

        if (latestReport == null || latestReport.getFinalDecision() == null) {
            log.info("交易对 {} 无历史分析报告，跳过", code);
            return Evaluation.SKIPPED;
        }

        // Build current market context
        String klineContext = marketDataService.buildMultiTimeframeContext(code, 30);
        String indicators = marketDataService.buildIndicatorSummary(code, 30);

        // Build evaluation prompt
        String prompt = """
                请对以下投资决策进行事后评估。

                ## 持仓信息
                - 交易对: %s
                - 持仓数量: %s
                - 买入均价: %s USDT
                - 当前价格: %s USDT
                - 盈亏: %.2f%%

                ## 原始决策（%s）
                %s

                ## 当前市场数据
                %s

                ## 技术指标
                %s

                请评估：
                1. 当初的决策是否正确？（正确/错误/部分正确）
                2. 当前市场走势是否推翻了原来的结论？
                3. 如果结论发生了变化，请详细说明原因
                4. 给出经验教训（如果有的话）
                5. 当前建议：继续持有 / 加仓 / 减仓 / 清仓

                请严格按以下JSON格式输出（不要输出其他内容）：
                {"correct":"正确/错误/部分正确","conclusion_changed":true/false,"reason":"原因说明","lesson":"经验教训","current_advice":"继续持有/加仓/减仓/清仓"}
                """.formatted(
                code,
                pos.quantity().stripTrailingZeros().toPlainString(),
                pos.avgCost().stripTrailingZeros().toPlainString(),
                pos.currentPrice().stripTrailingZeros().toPlainString(),
                pos.pnlRatio().doubleValue(),
                latestReport.getCreatedAt() != null ? latestReport.getCreatedAt().toString() : "未知",
                truncate(latestReport.getFinalDecision(), 500),
                klineContext != null ? truncate(klineContext, 1500) : "无数据",
                indicators != null ? truncate(indicators, 800) : "无数据"
        );

        ChatClient client = modelClientResolver.resolve("reflection");
        String response = client.prompt()
                .system("你是一位严格的投资复盘分析师，客观评估过去的投资决策。只输出JSON，不要其他内容。")
                .user(prompt)
                .call()
                .content();

        String json = response == null ? "" : response.strip();
        int fenceHeader = json.indexOf('\n');
        if (json.startsWith("```") && json.endsWith("```") && fenceHeader > 0 && fenceHeader < json.length() - 3) {
            json = json.substring(fenceHeader + 1, json.length() - 3).strip();
        }
        var evaluation = mapper.readTree(json);
        if (evaluation == null || !evaluation.path("conclusion_changed").isBoolean()) {
            throw new IllegalStateException("复盘模型没有返回有效的评估结果");
        }
        String lesson = evaluation.path("lesson").asText("");
        if (evaluation.path("conclusion_changed").asBoolean() || !lesson.isBlank()) {
            memoryService.reflect(code, truncate(latestReport.getFinalDecision(), 300), json, lesson);
            return Evaluation.STORED;
        }
        return Evaluation.EVALUATED;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private enum Evaluation { SKIPPED, EVALUATED, STORED }

    record Result(int evaluated, int skipped, int failed, int stored) {}
}
