package com.tradingagents.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent记忆服务 - 基于PgVector的两层经验记忆系统
 *
 * 第一层：情境记忆 (situation_memory) - 每次分析的结构化快照
 *   存储当时的市场状态、决策方向、关键依据
 *   召回时用当前市场情境做语义匹配
 *
 * 第二层：教训记忆 (reflection_memory) - 事后反思的经验教训
 *   存储决策对错、结论变化原因、可迁移的教训
 *   支持跨股票召回（通用教训不限于同一只股票）
 */
@Service
public class AgentMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryService.class);

    private final VectorStore vectorStore;

    public AgentMemoryService(ObjectProvider<VectorStore> vectorStore) {
        this.vectorStore = vectorStore.getIfAvailable();
    }

    // ===== 存储 =====

    /**
     * 存储情境记忆 - 结构化的分析快照
     * 内容设计为"情境描述"而非"报告摘要"，便于向量搜索匹配相似情境
     */
    public void storeSituationMemory(String stockCode, String stockName,
                                     String marketSituation, String decisionDirection,
                                     String decisionSummary) {
        if (vectorStore == null) return;
        String content = """
                [情境记忆] %s(%s)
                市场状态: %s
                决策方向: %s
                决策摘要: %s
                """.formatted(stockCode, stockName != null ? stockName : "",
                marketSituation, decisionDirection, decisionSummary);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("stock_code", stockCode);
        metadata.put("type", "situation_memory");
        metadata.put("decision_direction", decisionDirection);

        vectorStore.add(List.of(new Document(content, metadata)));
        log.info("Stored situation memory: stock={}, direction={}", stockCode, decisionDirection);
    }

    /**
     * 存储反思记忆 - 事后评估的经验教训
     * 教训类记忆可跨股票迁移，不锁定stock_code过滤
     */
    public void reflect(String stockCode, String decision, String outcome, String lesson) {
        if (vectorStore == null) return;
        String content = """
                [反思教训] %s
                原始决策: %s
                实际结果: %s
                经验教训: %s
                """.formatted(stockCode, decision, outcome, lesson);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("stock_code", stockCode);
        metadata.put("type", "reflection_memory");

        vectorStore.add(List.of(new Document(content, metadata)));
        log.info("Stored reflection for stock={}", stockCode);
    }

    // ===== 召回 =====

    /**
     * 两层召回：同股票情境 + 跨股票教训
     *
     * 1. 同股票：用当前市场情境query搜索该股的历史情境和反思（topK-1条）
     * 2. 跨股票：搜索所有股票的反思教训，找可迁移的通用经验（1条）
     * 3. 合并去重，反思类排在前面
     *
     * @param query 动态生成的情境描述（从当前K线/指标/情绪提取）
     */
    public String recallRelevantMemories(String stockCode, String query, int topK) {
        if (vectorStore == null) return "";
        StringBuilder result = new StringBuilder();

        // Phase 1: 同股票经验（情境+反思混合）
        try {
            List<Document> sameStock = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(Math.max(topK - 1, 2))
                            .similarityThreshold(0.55)
                            .filterExpression(new FilterExpressionBuilder().eq("stock_code", stockCode).build())
                            .build());

            if (sameStock != null && !sameStock.isEmpty()) {
                // 反思类排前面
                List<Document> reflections = sameStock.stream()
                        .filter(d -> "reflection_memory".equals(d.getMetadata().get("type")))
                        .toList();
                List<Document> situations = sameStock.stream()
                        .filter(d -> !"reflection_memory".equals(d.getMetadata().get("type")))
                        .toList();

                if (!reflections.isEmpty()) {
                    result.append("### 该股历史教训\n");
                    reflections.forEach(d -> result.append(d.getText()).append("\n---\n"));
                }
                if (!situations.isEmpty()) {
                    result.append("### 该股历史情境\n");
                    situations.forEach(d -> result.append(d.getText()).append("\n---\n"));
                }
            }
        } catch (Exception e) {
            log.warn("Same-stock memory recall failed: {}", e.getMessage());
        }

        // Phase 2: 跨股票通用教训（不限stock_code，只搜reflection类型）
        try {
            List<Document> crossStock = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(2)
                            .similarityThreshold(0.65)
                            .filterExpression("type == 'reflection_memory'")
                            .build());

            if (crossStock != null) {
                // 排除已在Phase1出现的同股票结果
                List<Document> newLessons = crossStock.stream()
                        .filter(d -> !stockCode.equals(d.getMetadata().get("stock_code")))
                        .limit(1)
                        .toList();

                if (!newLessons.isEmpty()) {
                    result.append("### 其他交易对的可借鉴教训\n");
                    newLessons.forEach(d -> result.append(d.getText()).append("\n---\n"));
                }
            }
        } catch (Exception e) {
            log.warn("Cross-stock memory recall failed: {}", e.getMessage());
        }

        if (result.isEmpty()) return "";
        return "## 历史经验参考\n\n" + result;
    }

    /**
     * 简单召回（供对话工具使用）- 按股票过滤
     */
    public String recallByStock(String stockCode, String query, int topK) {
        if (vectorStore == null) return "";
        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(0.55)
                            .filterExpression(new FilterExpressionBuilder().eq("stock_code", stockCode).build())
                            .build());

            if (results == null || results.isEmpty()) return "";
            return results.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n---\n", "## 历史经验参考\n", ""));
        } catch (Exception e) {
            log.warn("Memory recall failed: {}", e.getMessage());
            return "";
        }
    }
}
