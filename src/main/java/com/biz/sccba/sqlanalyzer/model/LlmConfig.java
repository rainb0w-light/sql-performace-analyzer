package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;

/**
 * 大模型配置模型
 */
@Data
public class LlmConfig {
    /**
     * 模型名称（唯一标识）
     */
    private String name;
    
    /**
     * 模型类型（openai/deepseek等，目前都使用openai兼容接口）
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
     * 模型名称（如 deepseek-chat, gpt-4等）
     */
    private String model;
    
    /**
     * 温度参数（0.0-2.0）
     */
    private Double temperature = 0.7;
}

