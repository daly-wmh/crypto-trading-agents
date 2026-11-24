package com.tradingagents.model;

import com.tradingagents.config.AgentModelConfigService;
import com.tradingagents.config.AgentModelConfigService.ResolvedProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型客户端解析器 - 按Agent的最新配置动态构建ChatClient
 *
 * - Ollama: 复用 Spring 管理的 OllamaChatModel bean
 * - OpenAI兼容: 动态创建 OpenAiChatModel。
 *   RestClientCustomizer (OllamaTimeoutConfig) 会自动应用到 OpenAiChatModel
 *   内部创建的 RestClient，使用 JdkClientHttpRequestFactory 避免系统代理。
 */
@Component
public class ModelClientResolver {

    private static final Logger log = LoggerFactory.getLogger(ModelClientResolver.class);

    private final OllamaChatModel ollamaModel;
    private final AgentModelConfigService configService;
    private final RestClient.Builder restClientBuilder;

    /** 缓存动态创建的OpenAI兼容ChatModel (按providerId:modelName) */
    private final Map<String, ChatModel> dynamicModels = new ConcurrentHashMap<>();

    public ModelClientResolver(OllamaChatModel ollamaModel,
                               AgentModelConfigService configService,
                               RestClient.Builder restClientBuilder) {
        this.ollamaModel = ollamaModel;
        this.configService = configService;
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * 根据agent的最新配置解析ChatClient
     */
    public ChatClient resolve(String agentId) {
        return resolve(agentId, null, null);
    }

    public ChatClient resolveStructured(String agentId, Map<String, Object> schema) {
        return resolve(agentId, null, null, schema);
    }

    public String modelKey(String agentId, String providerId, String model) {
        var selection = configService.selectModel(agentId, providerId, model);
        return selection.provider().providerId() + ":" + selection.modelName();
    }

    public ChatClient resolve(String agentId, String providerId, String model) {
        return resolve(agentId, providerId, model, null);
    }

    private ChatClient resolve(String agentId, String providerId, String model, Map<String, Object> schema) {
        var selection = configService.selectModel(agentId, providerId, model);
        ResolvedProvider rp = selection.provider();
        String modelName = selection.modelName();
        boolean hasModel = modelName != null && !modelName.isBlank();

        log.info("Resolve model for [{}]: type={}, providerId={}, model={}", agentId, rp.type(), rp.providerId(),
                hasModel ? modelName : "(默认)");

        ChatModel chatModel;
        ChatClient.Builder builder;
        if ("ollama".equals(rp.type())) {
            chatModel = "ollama".equals(rp.providerId()) ? ollamaModel :
                    dynamicModels.computeIfAbsent(rp.providerId() + ":" + modelName, ignored ->
                            OllamaChatModel.builder()
                                    .ollamaApi(OllamaApi.builder().baseUrl(rp.baseUrl())
                                            .restClientBuilder(restClientBuilder.clone()).build())
                                    .defaultOptions(OllamaOptions.builder().model(modelName).build())
                                    .build());
            builder = ChatClient.builder(chatModel);
            if (hasModel || schema != null) {
                var options = OllamaOptions.builder();
                if (hasModel) options.model(modelName);
                if (schema != null) options.format(schema).temperature(0.0);
                builder.defaultOptions(options.build());
            }
        } else {
            String cacheKey = rp.providerId() + ":" + modelName;
            chatModel = dynamicModels.computeIfAbsent(cacheKey, k -> createOpenAiModel(rp));
            builder = ChatClient.builder(chatModel);
            if (hasModel) {
                builder.defaultOptions(OpenAiChatOptions.builder().model(modelName).temperature(0.3).build());
            }
        }

        return builder.build();
    }

    private ChatModel createOpenAiModel(ResolvedProvider rp) {
        try {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(openAiBaseUrl(rp.baseUrl()))
                    .apiKey(rp.apiKey() == null ? "not-configured" : rp.apiKey())
                    .restClientBuilder(this.restClientBuilder.clone())
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(api)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create OpenAI model for provider '{}'", rp.providerId(), e);
            throw new RuntimeException("Cannot create model for provider: " + rp.providerId(), e);
        }
    }

    private String openAiBaseUrl(String url) {
        return url.strip().replaceAll("/+$", "").replaceFirst("/v1$", "");
    }

    public void clearCache() {
        dynamicModels.clear();
    }

    public void clearCache(String providerId) {
        dynamicModels.entrySet().removeIf(e -> e.getKey().startsWith(providerId + ":"));
    }
}
