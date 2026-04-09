package com.biz.sccba.sqlanalyzer.config;

import com.biz.sccba.sqlanalyzer.config.AiConfig.LlmConfigProperties;
import io.agentscope.core.tool.Toolkit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试配置 - 提供 Mock 的 Toolkit 和 AI 配置
 */
@TestConfiguration
public class TestAgentScopeConfig {

    /**
     * Mock Toolkit 用于测试
     * 使用 @MockBean 覆盖主配置中的 toolkit bean
     */
    @MockBean
    public Toolkit toolkit;

    /**
     * 提供空的 LlmConfigProperties 用于测试
     * 使用@Primary 覆盖自动配置的 bean
     */
    @Bean
    @Primary
    public LlmConfigProperties testLlmConfigProperties() {
        System.out.println("[TestConfig] 使用测试用 Mock LlmConfigProperties");
        LlmConfigProperties properties = new LlmConfigProperties();
        properties.setConfigs(java.util.Collections.emptyList());
        return properties;
    }
}
