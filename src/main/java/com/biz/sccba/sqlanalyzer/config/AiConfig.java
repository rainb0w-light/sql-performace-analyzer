package com.biz.sccba.sqlanalyzer.config;

import com.biz.sccba.sqlanalyzer.data.LlmConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Configuration only; model instances are created by AgentScopeLlmService. */
@Configuration
@EnableConfigurationProperties(AiConfig.LlmConfigProperties.class)
public class AiConfig {
    @ConfigurationProperties(prefix = "spring.llms")
    public static class LlmConfigProperties {
        private List<LlmConfig> configs;
        public List<LlmConfig> getConfigs() { return configs; }
        public void setConfigs(List<LlmConfig> configs) { this.configs = configs; }
    }
}
