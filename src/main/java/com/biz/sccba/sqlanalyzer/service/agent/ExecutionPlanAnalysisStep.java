package com.biz.sccba.sqlanalyzer.service.agent;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.SqlAgentResponse;
import com.biz.sccba.sqlanalyzer.service.LlmManagerService;
import com.biz.sccba.sqlanalyzer.service.PromptTemplateManagerService;
import com.biz.sccba.sqlanalyzer.service.SqlExecutionPlanService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

/**
 * 步骤 3: 执行计划评估
 * 生成执行计划，由 LLM 判定效率并评估添加索引的可行性
 */
public class ExecutionPlanAnalysisStep implements SqlAgentStep {

    private final SqlExecutionPlanService executionPlanService;
    private final LlmManagerService llmManagerService;
    private final PromptTemplateManagerService promptTemplateManagerService;

    public ExecutionPlanAnalysisStep(
            SqlExecutionPlanService executionPlanService,
            LlmManagerService llmManagerService,
            PromptTemplateManagerService promptTemplateManagerService) {
        this.executionPlanService = executionPlanService;
        this.llmManagerService = llmManagerService;
        this.promptTemplateManagerService = promptTemplateManagerService;
    }

    @Override
    public void execute(SqlAgentWorkflowContext context) {
        for (SqlAgentWorkflowContext.SqlScenario scenario : context.getScenarios()) {
            SqlAgentResponse.PlanEvaluationInfo evaluationInfo = new SqlAgentResponse.PlanEvaluationInfo();
            evaluationInfo.setScenarioName(scenario.getScenarioName());
            evaluationInfo.setSql(scenario.getFilledSql());

            try {
                ExecutionPlan plan = executionPlanService.getExecutionPlan(scenario.getFilledSql(), context.getDatasourceName());
                evaluationInfo.setExecutionPlan(plan);

                String templateContent = promptTemplateManagerService.getTemplateContent(PromptTemplateManagerService.TYPE_SQL_AGENT_PLAN_EVALUATION);
                PromptTemplate promptTemplate = new PromptTemplate(templateContent);
                Map<String, Object> variables = Map.of(
                    "instantiated_sql", scenario.getFilledSql(),
                    "execution_plan", plan.getRawJson() != null ? plan.getRawJson() : plan.toString()
                );
                String prompt = promptTemplate.render(variables);

                ChatClient chatClient = llmManagerService.getChatClient(context.getLlmName());
                String evaluation = chatClient.prompt().user(prompt).call().content();
                evaluationInfo.setEvaluation(evaluation);
            } catch (Exception e) {
                evaluationInfo.setEvaluation("评估失败: " + e.getMessage());
            }
            context.getPlanEvaluations().add(evaluationInfo);
        }
        
        context.setFinalReport(generateFinalReport(context));
    }

    @Override
    public String getName() {
        return "执行计划评估";
    }

    private String generateFinalReport(SqlAgentWorkflowContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("# SQL Agent 综合分析报告 (链式工作流)\n\n");
        sb.append("## 原始 SQL\n```sql\n").append(context.getSql()).append("\n```\n\n");
        sb.append("## 一、数据分布分析\n").append(context.getDistributionAnalysis()).append("\n\n");
        sb.append("## 二、各场景执行计划评估\n\n");
        
        for (SqlAgentResponse.PlanEvaluationInfo evaluation : context.getPlanEvaluations()) {
            sb.append("### 场景: ").append(evaluation.getScenarioName()).append("\n");
            sb.append("**实例化 SQL:**\n```sql\n").append(evaluation.getSql()).append("\n```\n\n");
            sb.append("**LLM 评估:**\n").append(evaluation.getEvaluation()).append("\n\n");
            sb.append("---\n\n");
        }

        return sb.toString();
    }
}

