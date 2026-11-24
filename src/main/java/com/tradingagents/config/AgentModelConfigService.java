package com.tradingagents.config;

import com.tradingagents.api.entity.AgentConfig;
import com.tradingagents.api.entity.ModelProvider;
import com.tradingagents.api.repository.AgentConfigRepository;
import com.tradingagents.api.service.ModelProviderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class AgentModelConfigService {

    private static final String GLOBAL = "__global__";
    private static final String OLLAMA = "ollama";
    private final AgentConfigRepository configs;
    private final ModelProviderService providers;

    public AgentModelConfigService(AgentConfigRepository configs, ModelProviderService providers) {
        this.configs = configs;
        this.providers = providers;
    }

    public AgentModelConfig getConfig(String id) {
        return configs.findByAgentId(id).map(AgentModelConfigService::toConfig)
                .orElseGet(() -> new AgentModelConfig(id, id, "", ""));
    }

    public List<AgentModelConfig> getAllConfigs() {
        return configs.findAllByOrderByExecutionOrderAsc().stream()
                .filter(config -> !GLOBAL.equals(config.getAgentId()))
                .map(AgentModelConfigService::toConfig)
                .toList();
    }

    @Transactional
    public void setConfig(String id, String label, String provider, String model) {
        saveConfig(id, label, provider, model);
    }

    public String getGlobalProvider() {
        return firstNonBlank(getConfig(GLOBAL).providerId(), OLLAMA);
    }

    public String getGlobalModelName() {
        return getConfig(GLOBAL).modelName();
    }

    @Transactional
    public void setGlobalDefault(String provider, String model) {
        saveConfig(GLOBAL, "全局默认", firstNonBlank(provider, OLLAMA), model);
    }

    private void saveConfig(String id, String label, String provider, String model) {
        AgentConfig config = configs.findByAgentId(id).orElseGet(AgentConfig::new);
        config.setAgentId(id);
        config.setDisplayName(label);
        config.setProviderId(firstNonBlank(provider));
        config.setModelName(firstNonBlank(model));
        configs.save(config);
    }

    public ModelSelection selectModel(String id, String preferredProvider, String preferredModel) {
        AgentModelConfig config = getConfig(id);
        AgentModelConfig global = getConfig(GLOBAL);
        String globalProvider = firstNonBlank(global.providerId(), OLLAMA);
        String providerId = firstNonBlank(preferredProvider, config.providerId(), globalProvider);
        ResolvedProvider provider = loadProvider(providerId);
        String globalModel = providerId.equals(globalProvider) ? global.modelName() : "";
        String model = firstNonBlank(preferredModel, config.modelName(), globalModel, provider.defaultModel());
        return new ModelSelection(provider, model);
    }

    private ResolvedProvider loadProvider(String providerId) {
        if (OLLAMA.equals(providerId)) {
            return new ResolvedProvider(OLLAMA, OLLAMA, null, null, "");
        }
        ModelProvider provider = providers.getById(Long.parseLong(providerId));
        return new ResolvedProvider(providerId, provider.getType(), provider.getBaseUrl(),
                providers.resolveApiKey(provider), provider.getModelName());
    }

    private static AgentModelConfig toConfig(AgentConfig config) {
        return new AgentModelConfig(config.getAgentId(), config.getDisplayName(),
                firstNonBlank(config.getProviderId()), firstNonBlank(config.getModelName()));
    }

    private static String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank())
                .map(String::strip).findFirst().orElse("");
    }

    public record AgentModelConfig(String agentId, String displayName, String providerId, String modelName) {}
    public record ResolvedProvider(String providerId, String type, String baseUrl, String apiKey, String defaultModel) {}
    public record ModelSelection(ResolvedProvider provider, String modelName) {}
}
