package com.biz.sccba.sqlanalyzer.config;

import com.biz.sccba.sqlanalyzer.data.DataSourceConfigModel;
import com.biz.sccba.sqlanalyzer.data.LlmConfig;
import com.biz.sccba.sqlanalyzer.service.SqlExecutionPlanService;
import com.biz.sccba.sqlanalyzer.tool.TableStructureTool;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试环境配置 - 提供 Mock Bean
 */
@Configuration
@Profile("test")
public class TestProfileConfig {

    @Bean
    @Primary
    public AiConfig.LlmConfigProperties llmConfigProperties() {
        AiConfig.LlmConfigProperties properties = new AiConfig.LlmConfigProperties();
        List<LlmConfig> configs = new ArrayList<>();
        LlmConfig config = new LlmConfig();
        config.setName("test");
        config.setApiKey("sk-test-key");
        config.setBaseUrl("https://api.test.com");
        config.setModel("test-model");
        config.setTemperature(0.7);
        configs.add(config);
        properties.setConfigs(configs);
        return properties;
    }

    @Bean
    @Primary
    public DataSourceConfig.DataSourceConfigProperties datasourceConfigProperties() {
        DataSourceConfig.DataSourceConfigProperties properties = new DataSourceConfig.DataSourceConfigProperties();
        List<DataSourceConfigModel> configs = new ArrayList<>();
        properties.setConfigs(configs);
        return properties;
    }

    @Bean
    @Primary
    public TableStructureTool tableStructureTool(SqlExecutionPlanService executionPlanService) {
        // 测试环境下提供 Mock 的 TableStructureTool
        return new TableStructureTool(executionPlanService);
    }
}
