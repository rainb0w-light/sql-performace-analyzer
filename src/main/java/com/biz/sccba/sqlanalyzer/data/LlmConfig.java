package com.biz.sccba.sqlanalyzer.data;

/**
 * 大模型配置模型
 */
public class LlmConfig {
    /**
     * 模型名称（唯一标识）
     */
    private String name;

    /**
     * 模型类型（openai/deepseek 等，目前都使用 openai 兼容接口）
     */
    private String type = "openai";

    /**
     * API Key
     */
    private String apiKey;

    /**
     * Base URL
     */
    private String baseUrl;

    /**
     * 模型名称（如 deepseek-chat, gpt-4 等）
     */
    private String model;

    /**
     * 温度参数（0.0-2.0）
     */
    private Double temperature = 0.7;

    /**
     * 模型描述
     */
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
