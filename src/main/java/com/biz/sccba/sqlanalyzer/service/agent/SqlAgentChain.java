package com.biz.sccba.sqlanalyzer.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 代理工作流链，管理并按顺序执行步骤
 */
public class SqlAgentChain {
    private static final Logger logger = LoggerFactory.getLogger(SqlAgentChain.class);
    
    private final List<SqlAgentStep> steps = new ArrayList<>();
    
    public SqlAgentChain addStep(SqlAgentStep step) {
        steps.add(step);
        return this;
    }
    
    public void execute(SqlAgentWorkflowContext context) {
        logger.info("开始执行 SQL 代理工作流链，共 {} 个步骤", steps.size());
        for (SqlAgentStep step : steps) {
            logger.info("正在执行步骤: {}", step.getName());
            try {
                step.execute(context);
            } catch (Exception e) {
                logger.error("步骤 {} 执行失败", step.getName(), e);
                throw new RuntimeException("工作流步骤 " + step.getName() + " 失败: " + e.getMessage(), e);
            }
        }
        logger.info("SQL 代理工作流链执行完成");
    }
}

