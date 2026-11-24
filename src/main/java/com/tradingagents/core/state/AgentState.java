package com.tradingagents.core.state;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局状态对象 - 在所有Agent节点间传递
 * 对标 TradingAgents-CN 的 LangGraph AgentState
 */
public class AgentState {

    // ===== 输入 =====
    private final String stockCode;
    private String stockName;
    private final LocalDateTime analysisTime;

    // ===== 分析师报告 =====
    private volatile String marketReport;
    private volatile String newsReport;
    private volatile String socialReport;
    private volatile String fundamentalReport;

    // ===== 辩论记录 =====
    private final List<String> bullArguments = new ArrayList<>();
    private final List<String> bearArguments = new ArrayList<>();

    // ===== 决策 =====
    private volatile String researchDecision;
    private volatile String tradeDecision;

    // ===== 风控辩论 =====
    private final List<String> riskAggressiveArgs = new ArrayList<>();
    private final List<String> riskConservativeArgs = new ArrayList<>();
    private final List<String> riskNeutralArgs = new ArrayList<>();
    private volatile String finalDecision;

    // ===== RAG经验记忆 =====
    private volatile String recalledMemories;

    // ===== 上下文数据 (供Agent工具调用使用) =====
    private final Map<String, Object> contextData = new ConcurrentHashMap<>();

    // ===== 启用的Agent列表 (null=全部启用) =====
    private volatile List<String> enabledAgents;

    // ===== 元数据 =====
    private final Map<String, Long> nodeTimings = new ConcurrentHashMap<>();
    private final AtomicInteger totalTokens = new AtomicInteger();

    public AgentState(String stockCode) {
        this.stockCode = stockCode;
        this.analysisTime = LocalDateTime.now();
    }

    // ===== 报告聚合 =====
    public String getAllAnalystReports() {
        StringBuilder sb = new StringBuilder();
        if (marketReport != null) sb.append("## 技术面分析\n").append(marketReport).append("\n\n");
        if (newsReport != null) sb.append("## 消息面分析\n").append(newsReport).append("\n\n");
        if (socialReport != null) sb.append("## 成交与波动\n").append(socialReport).append("\n\n");
        if (fundamentalReport != null) sb.append("## 基本面分析\n").append(fundamentalReport).append("\n\n");
        return sb.toString();
    }

    public String getDebateHistory() {
        StringBuilder sb = new StringBuilder();
        int rounds = Math.max(bullArguments.size(), bearArguments.size());
        for (int i = 0; i < rounds; i++) {
            if (i < bullArguments.size()) sb.append("### 多头观点 (第").append(i + 1).append("轮)\n").append(bullArguments.get(i)).append("\n\n");
            if (i < bearArguments.size()) sb.append("### 空头观点 (第").append(i + 1).append("轮)\n").append(bearArguments.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    public String getRiskDebateHistory() {
        StringBuilder sb = new StringBuilder();
        int rounds = Math.max(riskAggressiveArgs.size(), Math.max(riskConservativeArgs.size(), riskNeutralArgs.size()));
        for (int i = 0; i < rounds; i++) {
            if (i < riskAggressiveArgs.size()) sb.append("### 激进派 (第").append(i + 1).append("轮)\n").append(riskAggressiveArgs.get(i)).append("\n\n");
            if (i < riskConservativeArgs.size()) sb.append("### 保守派 (第").append(i + 1).append("轮)\n").append(riskConservativeArgs.get(i)).append("\n\n");
            if (i < riskNeutralArgs.size()) sb.append("### 中性派 (第").append(i + 1).append("轮)\n").append(riskNeutralArgs.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    // ===== Getters & Setters =====
    public String getStockCode() { return stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public LocalDateTime getAnalysisTime() { return analysisTime; }

    public String getMarketReport() { return marketReport; }
    public void setMarketReport(String marketReport) { this.marketReport = marketReport; }
    public String getNewsReport() { return newsReport; }
    public void setNewsReport(String newsReport) { this.newsReport = newsReport; }
    public String getSocialReport() { return socialReport; }
    public void setSocialReport(String socialReport) { this.socialReport = socialReport; }
    public String getFundamentalReport() { return fundamentalReport; }
    public void setFundamentalReport(String fundamentalReport) { this.fundamentalReport = fundamentalReport; }

    public List<String> getBullArguments() { return bullArguments; }
    public List<String> getBearArguments() { return bearArguments; }

    public String getResearchDecision() { return researchDecision; }
    public void setResearchDecision(String researchDecision) { this.researchDecision = researchDecision; }
    public String getTradeDecision() { return tradeDecision; }
    public void setTradeDecision(String tradeDecision) { this.tradeDecision = tradeDecision; }

    public List<String> getRiskAggressiveArgs() { return riskAggressiveArgs; }
    public List<String> getRiskConservativeArgs() { return riskConservativeArgs; }
    public List<String> getRiskNeutralArgs() { return riskNeutralArgs; }

    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String finalDecision) { this.finalDecision = finalDecision; }

    public List<String> getEnabledAgents() { return enabledAgents; }
    public void setEnabledAgents(List<String> enabledAgents) { this.enabledAgents = enabledAgents; }

    /** 判断某个agent是否被启用 (null/空列表=全部启用) */
    public boolean isAgentEnabled(String agentId) {
        return enabledAgents == null || enabledAgents.isEmpty() || enabledAgents.contains(agentId);
    }

    public String getRecalledMemories() { return recalledMemories; }
    public void setRecalledMemories(String recalledMemories) { this.recalledMemories = recalledMemories; }

    public Map<String, Object> getContextData() { return contextData; }
    public void putContext(String key, Object value) { contextData.put(key, value); }
    @SuppressWarnings("unchecked")
    public <T> T getContext(String key) { return (T) contextData.get(key); }

    public Map<String, Long> getNodeTimings() { return nodeTimings; }
    public void recordTiming(String nodeId, long ms) { nodeTimings.put(nodeId, ms); }

    public int getTotalTokens() { return totalTokens.get(); }
    public void addTokens(int tokens) { totalTokens.addAndGet(tokens); }
}
