package com.biz.sccba.sqlanalyzer.service.agent;

import com.biz.sccba.sqlanalyzer.service.DistributionBasedSqlFillerService;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 步骤 2: SQL 实例化
 * 根据分布信息，生成具体可执行的 SQL 场景
 */
public class SqlInstantiationStep implements SqlAgentStep {

    private final DistributionBasedSqlFillerService sqlFillerService;

    public SqlInstantiationStep(DistributionBasedSqlFillerService sqlFillerService) {
        this.sqlFillerService = sqlFillerService;
    }

    @Override
    public void execute(SqlAgentWorkflowContext context) {
        List<DistributionBasedSqlFillerService.SqlScenario> scenarios = 
            sqlFillerService.generateSqlScenarios(context.getSql(), context.getDatasourceName());
        
        List<SqlAgentWorkflowContext.SqlScenario> contextScenarios = scenarios.stream().map(s -> {
            SqlAgentWorkflowContext.SqlScenario scenario = new SqlAgentWorkflowContext.SqlScenario();
            scenario.setScenarioName(s.getScenarioName());
            scenario.setFilledSql(s.getFilledSql());
            scenario.setSampleValues(s.getSampleValues());
            return scenario;
        }).collect(Collectors.toList());
        
        context.setScenarios(contextScenarios);
    }

    @Override
    public String getName() {
        return "SQL 实例化";
    }
}

