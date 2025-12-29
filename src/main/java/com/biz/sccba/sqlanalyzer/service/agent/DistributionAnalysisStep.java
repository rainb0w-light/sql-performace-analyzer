package com.biz.sccba.sqlanalyzer.service.agent;

import com.biz.sccba.sqlanalyzer.model.dto.ColumnStatisticsDTO;
import com.biz.sccba.sqlanalyzer.service.ColumnStatisticsParserService;
import com.biz.sccba.sqlanalyzer.service.DataSourceManagerService;
import com.biz.sccba.sqlanalyzer.service.LlmManagerService;
import com.biz.sccba.sqlanalyzer.service.PromptTemplateManagerService;
import com.biz.sccba.sqlanalyzer.service.SqlExecutionPlanService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 步骤 1: 数据分布分析
 * 获取统计信息和直方图，调用 LLM 解读
 */
public class DistributionAnalysisStep implements SqlAgentStep {

    private final ColumnStatisticsParserService statisticsParserService;
    private final SqlExecutionPlanService executionPlanService;
    private final LlmManagerService llmManagerService;
    private final PromptTemplateManagerService promptTemplateManagerService;
    private final DataSourceManagerService dataSourceManagerService;

    public DistributionAnalysisStep(
            ColumnStatisticsParserService statisticsParserService,
            SqlExecutionPlanService executionPlanService,
            LlmManagerService llmManagerService,
            PromptTemplateManagerService promptTemplateManagerService,
            DataSourceManagerService dataSourceManagerService) {
        this.statisticsParserService = statisticsParserService;
        this.executionPlanService = executionPlanService;
        this.llmManagerService = llmManagerService;
        this.promptTemplateManagerService = promptTemplateManagerService;
        this.dataSourceManagerService = dataSourceManagerService;
    }

    @Override
    public void execute(SqlAgentWorkflowContext context) {
        List<String> tableNames = executionPlanService.parseTableNames(context.getSql());
        StringBuilder statsBuilder = new StringBuilder();
        List<ColumnStatisticsDTO> allStats = new ArrayList<>();

        for (String tableName : tableNames) {
            String databaseName = extractDatabaseName(context.getDatasourceName());
            List<ColumnStatisticsDTO> stats = statisticsParserService.getStatisticsFromMysql(context.getDatasourceName(), databaseName, tableName);
            allStats.addAll(stats);
            
            statsBuilder.append("表名: ").append(tableName).append("\n");
            for (ColumnStatisticsDTO dto : stats) {
                statsBuilder.append("  列名: ").append(dto.getColumnName()).append("\n");
                statsBuilder.append("  直方图类型: ").append(dto.getHistogramType()).append("\n");
                statsBuilder.append("  最小值: ").append(dto.getMinValue()).append("\n");
                statsBuilder.append("  最大值: ").append(dto.getMaxValue()).append("\n");
                statsBuilder.append("  采样值: ").append(dto.getSampleValues()).append("\n\n");
            }
        }
        
        context.setStatistics(allStats);

        String templateContent = promptTemplateManagerService.getTemplateContent(PromptTemplateManagerService.TYPE_SQL_AGENT_DISTRIBUTION);
        PromptTemplate promptTemplate = new PromptTemplate(templateContent);
        Map<String, Object> variables = Map.of(
            "sql", context.getSql(),
            "table_statistics", statsBuilder.toString()
        );
        String prompt = promptTemplate.render(variables);

        ChatClient chatClient = llmManagerService.getChatClient(context.getLlmName());
        String analysis = chatClient.prompt().user(prompt).call().content();
        context.setDistributionAnalysis(analysis);
    }

    @Override
    public String getName() {
        return "数据分布分析";
    }

    private String extractDatabaseName(String datasourceName) {
        try {
            return dataSourceManagerService.getAllDataSources().stream()
                    .filter(ds -> ds.getName().equals(datasourceName))
                    .findFirst()
                    .map(info -> {
                        String url = info.getUrl();
                        if (url.contains("/")) {
                            String dbPart = url.substring(url.lastIndexOf("/") + 1);
                            if (dbPart.contains("?")) {
                                dbPart = dbPart.substring(0, dbPart.indexOf("?"));
                            }
                            return dbPart;
                        }
                        return "test_db";
                    }).orElse("test_db");
        } catch (Exception e) {
            return "test_db";
        }
    }
}

