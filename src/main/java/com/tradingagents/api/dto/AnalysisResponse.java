package com.tradingagents.api.dto;

import com.tradingagents.core.state.AgentState;
import com.tradingagents.trading.dto.PaperSignal;

import java.time.LocalDateTime;
import java.util.Map;

public record AnalysisResponse(
        Long reportId,
        String stockCode,
        String stockName,
        LocalDateTime analysisTime,
        String marketReport,
        String newsReport,
        String socialReport,
        String fundamentalReport,
        String researchDecision,
        String tradeDecision,
        String debate,
        String finalDecision,
        PaperSignal paperSignal,
        Map<String, Long> nodeTimings,
        long totalDurationMs,
        int totalTokens
) {
    public static AnalysisResponse from(AgentState state, long totalMs, Long reportId) {
        return new AnalysisResponse(
                reportId,
                state.getStockCode(),
                state.getStockName(),
                state.getAnalysisTime(),
                state.getMarketReport(),
                state.getNewsReport(),
                state.getSocialReport(),
                state.getFundamentalReport(),
                state.getResearchDecision(),
                state.getTradeDecision(),
                state.getDebateHistory(),
                state.getFinalDecision(),
                PaperSignal.from(state.getFinalDecision()).orElse(null),
                state.getNodeTimings(),
                totalMs,
                state.getTotalTokens()
        );
    }
}
