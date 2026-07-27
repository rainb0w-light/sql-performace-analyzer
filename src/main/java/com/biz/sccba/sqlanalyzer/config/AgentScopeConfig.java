package com.biz.sccba.sqlanalyzer.config;

import com.biz.sccba.sqlanalyzer.agent.ReadOnlySqlTools;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * AgentScope 配置类
 */
@Configuration
public class AgentScopeConfig {

    /**
     * 创建并注册 Toolkit Bean
     */
    @Bean
    @Profile("!test")
    public Toolkit toolkit(ReadOnlySqlTools sqlTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registration().tool(sqlTools).apply();
        return toolkit;
    }

    @Bean
    @Profile("test")
    public Toolkit testToolkit() {
        return new Toolkit();
    }
}
