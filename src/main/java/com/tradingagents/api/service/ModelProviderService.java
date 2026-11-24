package com.tradingagents.api.service;

import com.tradingagents.api.entity.ModelProvider;
import com.tradingagents.api.repository.AgentConfigRepository;
import com.tradingagents.api.repository.ModelProviderRepository;
import com.tradingagents.api.repository.PipelineNodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

@Service
public class ModelProviderService {

    private final ModelProviderRepository providers;
    private final AgentConfigRepository configs;
    private final PipelineNodeRepository pipeline;

    public ModelProviderService(ModelProviderRepository providers, AgentConfigRepository configs,
                                PipelineNodeRepository pipeline) {
        this.providers = providers;
        this.configs = configs;
        this.pipeline = pipeline;
    }

    public List<ModelProvider> getAll() {
        return providers.findAll();
    }

    public ModelProvider getById(Long id) {
        return providers.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型源不存在"));
    }

    public ModelProvider create(ModelProvider provider) {
        validate(provider);
        provider.setId(null);
        return providers.save(provider);
    }

    @Transactional
    public ModelProvider update(Long id, ModelProvider updated) {
        validate(updated);
        ModelProvider existing = getById(id);
        existing.setName(updated.getName());
        existing.setType(updated.getType());
        existing.setBaseUrl(updated.getBaseUrl());
        existing.setApiKeyEnvVar(updated.getApiKeyEnvVar());
        existing.setModelName(updated.getModelName());
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        String key = id.toString();
        boolean usedInConfig = configs.findAll().stream().anyMatch(config -> key.equals(config.getProviderId()));
        boolean usedInPipeline = pipeline.findAll().stream().anyMatch(node -> key.equals(node.getProviderId()));
        if (usedInConfig || usedInPipeline) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该模型源仍在使用中");
        }
        providers.delete(getById(id));
    }

    public String resolveApiKey(ModelProvider provider) {
        if (!"openai".equals(provider.getType())) return null;
        String variable = provider.getApiKeyEnvVar();
        if (variable == null || variable.isBlank()) return null;
        return System.getenv(variable);
    }

    private static void validate(ModelProvider provider) {
        if (!"openai".equals(provider.getType()) && !"ollama".equals(provider.getType())) {
            throw badRequest("请使用 Ollama 或 OpenAI 兼容模型源");
        }
        try {
            URI url = URI.create(provider.getBaseUrl());
            if (url.getHost() == null || url.getUserInfo() != null
                    || url.getQuery() != null || url.getFragment() != null
                    || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))) {
                throw badRequest("请输入有效的模型服务地址");
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw badRequest("请输入有效的模型服务地址");
        }
        String name = provider.getName();
        String model = provider.getModelName();
        if (name == null || name.isBlank() || name.length() > 50
                || model == null || model.isBlank() || model.length() > 100) {
            throw badRequest("请填写模型源名称和模型名");
        }
        String keyVariable = provider.getApiKeyEnvVar();
        if (keyVariable != null && !keyVariable.isBlank() && !keyVariable.matches("[A-Za-z_][A-Za-z0-9_]{0,99}")) {
            throw badRequest("API Key 环境变量名格式不正确");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
