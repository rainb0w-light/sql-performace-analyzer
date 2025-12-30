package com.biz.sccba.sqlanalyzer.model.response;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * SQL Agent 分析响应
 */
@Data
public class SqlAgentResponse {
    private String originalSql;
    
    // Phase 1: Distribution Analysis
    private String distributionAnalysis;
    
    // Phase 2: Instantiated SQLs
    private List<InstantiatedSqlInfo> instantiatedSqls;
    
    // Phase 3: Plan Evaluations
    private List<PlanEvaluationInfo> planEvaluations;
    
    private String finalReport;

    @Data
    public static class InstantiatedSqlInfo {
        private String scenarioName;
        private String sql;
        private Map<String, Object> parameters;
    }

    @Data
    public static class PlanEvaluationInfo {
        private String scenarioName;
        private String sql;
        private ExecutionPlan executionPlan;
        private String evaluation;
    }
}



