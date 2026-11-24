package com.tradingagents.api.controller;

import com.tradingagents.config.AgentModelConfigService;
import com.tradingagents.config.AgentModelConfigService.AgentModelConfig;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent模型配置API
 */
@RestController
@RequestMapping("/api/agent-config")
public class AgentConfigController {

    private final AgentModelConfigService configService;
    private final String ollamaBaseUrl;
    private final WebClient webClient;

    public AgentConfigController(AgentModelConfigService configService,
                                  @Value("${spring.ai.ollama.base-url}") String ollamaBaseUrl) {
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .responseTimeout(Duration.ofSeconds(4))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);
        this.configService = configService;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.webClient =  WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    /** 获取所有Agent的模型配置 */
    @GetMapping
    public List<AgentModelConfig> getAll() {
        return configService.getAllConfigs();
    }

    /** 获取Ollama本地已安装的模型列表 */
    @GetMapping("/ollama-models")
    public List<String> getOllamaModels() {
        try {
            String response = webClient.get()
                    .uri(ollamaBaseUrl.replaceAll("/+$", "") + "/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null) return List.of();
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
            com.fasterxml.jackson.databind.JsonNode models = root.path("models");
            if (!models.isArray()) return List.of();
            return java.util.stream.StreamSupport.stream(models.spliterator(), false)
                    .map(m -> m.path("name").asText(""))
                    .filter(n -> !n.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 获取全局默认模型 */
    @GetMapping("/global-default")
    public Map<String, String> getGlobalDefault() {
        return Map.of(
                "providerId", configService.getGlobalProvider(),
                "modelName", configService.getGlobalModelName()
        );
    }

    /** 设置全局默认模型 */
    @PutMapping("/global-default")
    public Map<String, String> setGlobalDefault(@RequestBody Map<String, String> body) {
        configService.setGlobalDefault(
                body.getOrDefault("providerId", "ollama"),
                body.getOrDefault("modelName", "")
        );
        return getGlobalDefault();
    }

    /** 更新单个Agent的模型配置 */
    @PutMapping("/{agentId}")
    public AgentModelConfig update(@PathVariable String agentId, @RequestBody Map<String, String> body) {
        String providerId = body.getOrDefault("providerId", "");
        String modelName = body.getOrDefault("modelName", "");
        AgentModelConfig existing = configService.getConfig(agentId);
        String displayName = existing != null ? existing.displayName() : agentId;
        configService.setConfig(agentId, displayName, providerId, modelName);
        return configService.getConfig(agentId);
    }

    /** 批量更新 */
    @PutMapping("/batch")
    public List<AgentModelConfig> batchUpdate(@RequestBody List<Map<String, String>> items) {
        for (Map<String, String> item : items) {
            String agentId = item.get("agentId");
            if (agentId != null) {
                String providerId = item.getOrDefault("providerId", "");
                String modelName = item.getOrDefault("modelName", "");
                AgentModelConfig existing = configService.getConfig(agentId);
                String displayName = existing != null ? existing.displayName() : agentId;
                configService.setConfig(agentId, displayName, providerId, modelName);
            }
        }
        return configService.getAllConfigs();
    }
}
