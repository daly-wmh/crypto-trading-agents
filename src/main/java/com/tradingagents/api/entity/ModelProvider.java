package com.tradingagents.api.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型提供商配置实体
 */
@Data
@Entity
@Table(name = "model_provider")
public class ModelProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String type; // "ollama" or "openai"

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "api_key_env_var", length = 100)
    private String apiKeyEnvVar;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
