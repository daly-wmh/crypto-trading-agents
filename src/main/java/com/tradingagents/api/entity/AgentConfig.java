package com.tradingagents.api.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Agent模型配置实体 - 持久化每个Agent的模型选择
 */
@Data
@Entity
@Table(name = "agent_config")
public class AgentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, unique = true, length = 50)
    private String agentId;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    /** 模型提供商ID (对应model_provider表)，空=跟随全局默认 */
    @Column(name = "provider_id", length = 50)
    private String providerId;

    /** 可选模型名覆盖，空=使用提供商的默认模型 */
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "execution_order")
    private Integer executionOrder;

    @Column(name = "is_deep")
    private Boolean deep;

    @PrePersist
    void prePersist() {
        if (executionOrder == null) executionOrder = 0;
        if (deep == null) deep = false;
    }
}
