package com.biz.sccba.sqlanalyzer.service.agent;

/**
 * SQL 代理工作流中的单个步骤接口
 */
public interface SqlAgentStep {
    /**
     * 执行当前步骤
     * 
     * @param context 工作流上下文
     */
    void execute(SqlAgentWorkflowContext context);
    
    /**
     * 获取步骤名称
     */
    String getName();
}

