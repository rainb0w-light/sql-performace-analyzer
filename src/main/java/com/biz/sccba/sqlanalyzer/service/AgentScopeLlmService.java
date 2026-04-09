package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.config.AiConfig;
import com.biz.sccba.sqlanalyzer.data.LlmConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope LLM 服务
 * 管理和提供 LLM 模型实例
 */
@Service
public class AgentScopeLlmService {

    @Getter
    private final Map<String, Model> models = new ConcurrentHashMap<>();

    @Getter
    private String defaultModelName;

    @Autowired
    private AiConfig.LlmConfigProperties llmConfigProperties;

    @PostConstruct
    public void init() {
        List<LlmConfig> configs = llmConfigProperties.getConfigs();
        if (configs == null || configs.isEmpty()) {
            System.out.println("[AgentScopeLlmService] 未配置任何 LLM 模型");
            return;
        }

        for (LlmConfig config : configs) {
            validateConfig(config);
            Model model = createModel(config);
            models.put(config.getName(), model);
            System.out.println("[AgentScopeLlmService] 注册 LLM 模型：" + config.getName() + " (" + config.getModel() + ")");
        }

        // 设置默认模型
        this.defaultModelName = configs.get(0).getName();
        System.out.println("[AgentScopeLlmService] 默认 LLM 模型：" + defaultModelName);
    }

    /**
     * 获取指定名称的 AgentScope Model
     */
    public Optional<Model> getModel(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultModel();
        }
        return Optional.ofNullable(models.get(name));
    }

    /**
     * 获取默认 AgentScope Model
     */
    public Optional<Model> getDefaultModel() {
        Model model = models.get(defaultModelName);
        if (model != null) {
            return Optional.of(model);
        }
        return Optional.empty();
    }

    /**
     * 获取所有可用的 LLM 名称
     */
    public List<String> getAvailableModelNames() {
        return new ArrayList<>(models.keySet());
    }

    /**
     * 获取 LLM 配置信息
     */
    public List<LlmInfo> getLlmInfos() {
        List<LlmInfo> infos = new ArrayList<>();
        List<LlmConfig> configs = llmConfigProperties.getConfigs();
        if (configs != null) {
            for (LlmConfig config : configs) {
                infos.add(new LlmInfo(
                    config.getName(),
                    config.getModel(),
                    config.getBaseUrl(),
                    config.getDescription(),
                    config.getName().equals(defaultModelName)
                ));
            }
        }
        return infos;
    }

    /**
     * 切换默认模型
     */
    public void setDefaultModel(String name) {
        if (!models.containsKey(name)) {
            throw new IllegalArgumentException("未找到 LLM 模型：" + name);
        }
        this.defaultModelName = name;
        System.out.println("[AgentScopeLlmService] 切换默认 LLM 模型为：" + name);
    }

    /**
     * 验证配置
     */
    private void validateConfig(LlmConfig config) {
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new IllegalStateException("LLM 配置中 name 不能为空");
        }
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalStateException("LLM 配置中 api-key 不能为空：" + config.getName());
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            throw new IllegalStateException("LLM 配置中 base-url 不能为空：" + config.getName());
        }
        if (config.getModel() == null || config.getModel().trim().isEmpty()) {
            throw new IllegalStateException("LLM 配置中 model 不能为空：" + config.getName());
        }
    }

    /**
     * 创建 AgentScope Model 实例（使用原生的 OpenAIChatModel）
     */
    private Model createModel(LlmConfig config) {
        GenerateOptions options = null;
        if (config.getTemperature() != null) {
            options = GenerateOptions.builder()
                .temperature(config.getTemperature())
                .build();
        }

        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
            .apiKey(config.getApiKey())
            .baseUrl(config.getBaseUrl())
            .modelName(config.getModel());

        if (options != null) {
            builder.generateOptions(options);
        }

        return builder.build();
    }

    /**
     * LLM 信息
     */
    @Getter
    public static class LlmInfo {
        private final String name;
        private final String model;
        private final String baseUrl;
        private final String description;
        private final boolean isDefault;

        public LlmInfo(String name, String model, String baseUrl, String description, boolean isDefault) {
            this.name = name;
            this.model = model;
            this.baseUrl = baseUrl;
            this.description = description;
            this.isDefault = isDefault;
        }
    }
}
