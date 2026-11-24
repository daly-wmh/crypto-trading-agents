package com.tradingagents.api.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 流水线节点 - 定义分析链中每个 Agent 的编排位置和行为
 * 同一 stage_order 的节点并行执行，不同 stage_order 按顺序串行
 */
@Data
@Entity
@Table(name = "pipeline_node")
public class PipelineNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 节点唯一标识 (如 market_analyst, investment_decision) */
    @Column(nullable = false, unique = true, length = 50)
    private String nodeKey;

    /** 显示名称 */
    @Column(nullable = false, length = 50)
    private String displayName;

    /**
     * 上下文类型 - 决定注入什么数据给 Agent
     * ANALYST_MARKET / ANALYST_NEWS / ANALYST_SOCIAL / ANALYST_FUNDAMENTAL
     * DECISION / TRADER / RISK / CUSTOM
     */
    @Column(nullable = false, length = 30)
    private String contextType;

    /** 系统提示词 (可编辑) */
    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    /** 阶段序号 - 相同值并行执行 */
    @Column(nullable = false)
    private Integer stageOrder;

    /** 阶段内排序 (影响并行节点的显示顺序) */
    @Column(nullable = false)
    private Integer positionInStage;

    /** 模型源 ID (空=跟随全局) */
    @Column(length = 50)
    private String providerId;

    /** 模型名 (空=使用provider默认) */
    @Column(length = 100)
    private String modelName;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 阶段类型: AUTO=自动(1个串行/多个并行), DEBATE=辩论模式 */
    @Column(length = 20)
    private String stageType = "AUTO";

    /** 辩论轮数 (仅 DEBATE 模式生效) */
    private Integer debateRounds = 2;
}
