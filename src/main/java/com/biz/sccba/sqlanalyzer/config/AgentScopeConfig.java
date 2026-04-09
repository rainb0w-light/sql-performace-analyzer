package com.biz.sccba.sqlanalyzer.config;

import com.biz.sccba.sqlanalyzer.tool.TableStructureTool;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * AgentScope 配置类
 */
@Configuration
@Profile("!test") // 测试环境不加载此配置
public class AgentScopeConfig {

    /**
     * 创建并注册 Toolkit Bean
     */
    @Bean
    public Toolkit toolkit(TableStructureTool tableStructureTool) {
        System.out.println("初始化 AgentScope Toolkit");
        Toolkit toolkit = new Toolkit();

        // 注册工具
        toolkit.registration()
                .tool(tableStructureTool)
                .group("sql-analyzer")
                .apply();

        System.out.println("已注册工具到 Toolkit: " + toolkit.getToolNames());
        return toolkit;
    }
}
