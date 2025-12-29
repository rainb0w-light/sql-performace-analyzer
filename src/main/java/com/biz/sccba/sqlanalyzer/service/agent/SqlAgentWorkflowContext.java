package com.biz.sccba.sqlanalyzer.service.agent;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.SqlAgentResponse;
import com.biz.sccba.sqlanalyzer.model.dto.ColumnStatisticsDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL 代理工作流上下文，存储各个步骤的中间结果
 */
@Data
public class SqlAgentWorkflowContext {
    private String sql;
    private String datasourceName;
    private String llmName;
    
    // 步骤 1: 分布分析结果
    private String distributionAnalysis;
    private List<ColumnStatisticsDTO> statistics;
    
    // 步骤 2: 实例化结果
    private List<SqlScenario> scenarios = new ArrayList<>();
    
    // 步骤 3: 执行计划评估结果
    private List<SqlAgentResponse.PlanEvaluationInfo> planEvaluations = new ArrayList<>();
    
    private String finalReport;

    @Data
    public static class SqlScenario {
        private String scenarioName;
        private String filledSql;
        private Map<String, Object> sampleValues;
    }
}

