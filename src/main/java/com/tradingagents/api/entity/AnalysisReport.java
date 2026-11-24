package com.tradingagents.api.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分析报告持久化实体
 */
@Data
@Entity
@Table(name = "analysis_report")
public class AnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", length = 16, nullable = false)
    private String stockCode;

    @Column(name = "stock_name", length = 50)
    private String stockName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "market_report", columnDefinition = "TEXT")
    private String marketReport;

    @Column(name = "news_report", columnDefinition = "TEXT")
    private String newsReport;

    @Column(name = "social_report", columnDefinition = "TEXT")
    private String socialReport;

    @Column(name = "fundamental_report", columnDefinition = "TEXT")
    private String fundamentalReport;

    @Column(name = "bull_arguments", columnDefinition = "TEXT")
    private String bullArguments;

    @Column(name = "bear_arguments", columnDefinition = "TEXT")
    private String bearArguments;

    @Column(name = "research_decision", columnDefinition = "TEXT")
    private String researchDecision;

    @Column(name = "trade_decision", columnDefinition = "TEXT")
    private String tradeDecision;

    @Column(name = "risk_debate", columnDefinition = "TEXT")
    private String riskDebate;

    @Column(name = "final_decision", columnDefinition = "TEXT")
    private String finalDecision;

    @Column(name = "recalled_memories", columnDefinition = "TEXT")
    private String recalledMemories;

    @Column(name = "model_provider", length = 30)
    private String modelProvider;

    @Column(name = "quick_model", length = 100)
    private String quickModel;

    @Column(name = "deep_model", length = 100)
    private String deepModel;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    // ===== 断点续连 =====
    @Column(name = "status", length = 20)
    private String status; // RUNNING, COMPLETED, FAILED, INTERRUPTED

    @Column(name = "stage", length = 50)
    private String stage; // 当前执行到的阶段ID

    @Column(name = "state_snapshot", columnDefinition = "TEXT")
    private String stateSnapshot; // AgentState中间结果JSON快照

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
