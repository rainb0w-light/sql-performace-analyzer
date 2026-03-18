package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;

/**
 * LLM 配置模型
 */
@Data
public class LlmConfig {
    private String name;
    private String baseUrl;
    private String apiKey;
    private String modelName;
}
