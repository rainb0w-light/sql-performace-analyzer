package com.biz.sccba.sqlanalyzer.llm.service;

import com.biz.sccba.sqlanalyzer.data.TableStructure;
import com.biz.sccba.sqlanalyzer.error.AgentErrorCode;
import com.biz.sccba.sqlanalyzer.error.AgentException;
import com.biz.sccba.sqlanalyzer.llm.context.PromptTemplateEngine;
import com.biz.sccba.sqlanalyzer.model.*;
import com.biz.sccba.sqlanalyzer.repository.IndexOptimizationReportRepository;
import com.biz.sccba.sqlanalyzer.repository.ExecutionPlanRepository;
import com.biz.sccba.sqlanalyzer.service.LlmManagerService;
import com.biz.sccba.sqlanalyzer.service.PromptTemplateManagerService;
import com.biz.sccba.sqlanalyzer.service.SqlExecutionPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 阶段4：生成索引优化报告。
 */
@Service
public class IndexReportService {

    private static final Logger logger = LoggerFactory.getLogger(IndexReportService.class);

    @Autowired
    private SqlExecutionPlanService executionPlanService;

    @Autowired
    private ExecutionPlanRepository executionPlanRepository;

    @Autowired
    private IndexOptimizationReportRepository optimizationReportRepository;

    @Autowired
    private PromptTemplateManagerService promptTemplateManagerService;

    @Autowired
    private LlmManagerService llmManagerService;

    @Autowired
    private PromptTemplateEngine templateEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public IndexOptimizationReport generateIndexOptimizationReport(String namespace,
                                                                   String datasourceName,
                                                                   String llmName,
                                                                   List<SqlPlanAnalysisResult> analysisResults
                                                                   ) throws AgentException {
        logger.info("阶段4：LLM 综合分析阶段 - namespace: {}", namespace);

        List<TableStructure> allTableStructures = collectTableStructures(analysisResults, datasourceName);
        Map<String, Long> tableRowsMap = buildTableRowsMap(allTableStructures);

        String tableStructuresJson = writeValue(allTableStructures);
        String analysisResultsJson = writeValue(analysisResults);

        String tableStructuresText = formatTableStructures(allTableStructures);
        String analysisResultsText = formatAnalysisResults(analysisResults, tableRowsMap);

        String templateContent = promptTemplateManagerService.getTemplateContent(
                PromptTemplateManagerService.TYPE_SQL_INDEX_OPTIMIZATION);
        String prompt = templateEngine.render(templateContent, Map.of(
                "table_structures", tableStructuresText,
                "analysis_results", analysisResultsText
        ));

       String response = llmManagerService.getChatClient(llmName).prompt().user(prompt).call().content();


        logger.info("LLM 返回的 Markdown 报告长度: {}", response != null ? response.length() : 0);

        IndexOptimizationReport report = new IndexOptimizationReport();
        report.setMapperId(null);
        report.setNamespace(namespace);
        report.setReportContent(response);
        report.setTableStructuresJson(tableStructuresJson);
        report.setAnalysisResultsJson(analysisResultsJson);
        report.setDatasourceName(datasourceName);
        report.setLlmName(llmName);

        IndexOptimizationReport saved = optimizationReportRepository.save(report);
        logger.info("索引优化报告已保存，ID: {}", saved.getId());
        return saved;
    }

    private List<TableStructure> collectTableStructures(List<SqlPlanAnalysisResult> analysisResults,
                                                        String datasourceName) {
        Set<String> tableNames = new HashSet<>();
        for (SqlPlanAnalysisResult result : analysisResults) {
            List<String> tables = executionPlanService.parseTableNames(result.getOriginalSql());
            tableNames.addAll(tables);
        }
        return executionPlanService.getTableStructuresByNames(new ArrayList<>(tableNames), datasourceName);
    }

    private Map<String, Long> buildTableRowsMap(List<TableStructure> structures) {
        Map<String, Long> tableRowsMap = new HashMap<>();
        for (TableStructure structure : structures) {
            if (structure.getStatistics() != null && structure.getStatistics().getRows() != null) {
                tableRowsMap.put(structure.getTableName(), structure.getStatistics().getRows());
            }
        }
        return tableRowsMap;
    }

    private String formatAnalysisResults(List<SqlPlanAnalysisResult> analysisResults,
                                         Map<String, Long> tableRowsMap) {
        StringBuilder analysisResultsText = new StringBuilder();
        analysisResultsText.append(String.format("共 %d 个 SQL 分析结果：\n\n", analysisResults.size()));
        for (int i = 0; i < analysisResults.size(); i++) {
            SqlPlanAnalysisResult result = analysisResults.get(i);
            analysisResultsText.append(String.format("=== SQL %d ===\n", i + 1));
            analysisResultsText.append(String.format("Mapper ID: %s\n", result.getMapperId()));
            analysisResultsText.append(String.format("SQL: %s\n", result.getOriginalSql()));
            analysisResultsText.append(String.format("分类: %s\n", result.getCategory()));

            List<String> sqlTableNames = executionPlanService.parseTableNames(result.getOriginalSql());
            if (SqlPlanAnalysisResult.CATEGORY_NO_INDEX.equals(result.getCategory())) {
                appendNoIndexDetails(result, analysisResultsText, sqlTableNames, tableRowsMap);
            } else if (SqlPlanAnalysisResult.CATEGORY_PLAN_SHIFT.equals(result.getCategory())
                    && result.getPlanShiftDetailsJson() != null) {
                appendPlanShiftDetails(result, analysisResultsText, sqlTableNames, tableRowsMap);
            }
            analysisResultsText.append("\n");
        }
        return analysisResultsText.toString();
    }

    private void appendNoIndexDetails(SqlPlanAnalysisResult result,
                                      StringBuilder sb,
                                      List<String> sqlTableNames,
                                      Map<String, Long> tableRowsMap) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> recordIds = objectMapper.readValue(result.getExecutionPlanRecordIds(), List.class);
            sb.append("验证场景明细（全表扫描）:\n");
            for (Long recordId : recordIds) {
                executionPlanRepository.findById(recordId).ifPresent(record -> {
                    // 确保字段已解析
                    if (record.getAccessType() == null && record.getRawJson() != null && !record.getRawJson().isEmpty()) {
                        record.parseFromRawJson();
                    }
                    sb.append(String.format("   填充SQL: %s\n", record.getFilledSql()));
                    sb.append(String.format("   访问方式: %s\n",
                            record.getAccessType() != null ? record.getAccessType() : "ALL"));
                    sb.append(String.format("   扫描行数: %s\n",
                            record.getRowsExaminedPerScan() != null ? record.getRowsExaminedPerScan() : "N/A"));
                    for (String tableName : sqlTableNames) {
                        Long tableRows = tableRowsMap.get(tableName);
                        if (tableRows != null && record.getRowsExaminedPerScan() != null) {
                            double scanRatio = (double) record.getRowsExaminedPerScan() / tableRows * 100;
                            sb.append(String.format(
                                    "   扫描比例: %.2f%% (扫描行数 %d / 表总行数 %d)\n",
                                    scanRatio, record.getRowsExaminedPerScan(), tableRows));
                            if (scanRatio > 30) {
                                sb.append("   ⚠️ 警告: 扫描比例超过30%，即使有索引也可能选择性差\n");
                            }
                        }
                    }
                    sb.append("\n");
                });
            }
        } catch (Exception e) {
            logger.warn("读取执行计划记录失败: {}", e.getMessage());
            sb.append("无法读取详细场景信息\n\n");
        }
    }

    private void appendPlanShiftDetails(SqlPlanAnalysisResult result,
                                        StringBuilder sb,
                                        List<String> sqlTableNames,
                                        Map<String, Long> tableRowsMap) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> details = objectMapper.readValue(
                    result.getPlanShiftDetailsJson(), Map.class);

            sb.append("执行计划偏移详情:\n");
            sb.append(String.format("不同索引数量: %d\n",
                    details.get("differentIndexes") != null ?
                            ((List<?>) details.get("differentIndexes")).size() : 0));
            sb.append(String.format("验证场景数: %d\n\n",
                    details.get("scenarioCount") != null ? details.get("scenarioCount") : 0));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> scenarioIndexes =
                    (List<Map<String, Object>>) details.get("scenarioIndexes");
            if (scenarioIndexes != null && !scenarioIndexes.isEmpty()) {
                sb.append("验证场景明细:\n");
                for (Map<String, Object> sc : scenarioIndexes) {
                    sb.append(String.format("   命中索引: %s\n",
                            sc.get("indexName") != null ? sc.get("indexName") : "FULL_TABLE_SCAN"));
                    sb.append(String.format("   访问方式: %s\n",
                            sc.get("accessType") != null ? sc.get("accessType") : "ALL"));
                    sb.append(String.format("   扫描行数: %s\n",
                            sc.get("rowsExamined") != null ? sc.get("rowsExamined") : "N/A"));

                    if (sc.get("rowsExamined") != null) {
                        Long rowsExamined = sc.get("rowsExamined") instanceof Number ?
                                ((Number) sc.get("rowsExamined")).longValue() : null;
                        if (rowsExamined != null) {
                            for (String tableName : sqlTableNames) {
                                Long tableRows = tableRowsMap.get(tableName);
                                if (tableRows != null && tableRows > 0) {
                                    double scanRatio = (double) rowsExamined / tableRows * 100;
                                    sb.append(String.format(
                                            "   扫描比例: %.2f%% (扫描行数 %d / 表总行数 %d)\n",
                                            scanRatio, rowsExamined, tableRows));
                                    if (scanRatio > 30) {
                                        sb.append("   ⚠️ 警告: 扫描比例超过30%，索引选择性可能较差\n");
                                    }
                                }
                            }
                        }
                    }
                    sb.append("\n");
                }

                Set<String> differentIndexes = new HashSet<>();
                for (Map<String, Object> sc : scenarioIndexes) {
                    String indexName = sc.get("indexName") != null ?
                            sc.get("indexName").toString() : "FULL_TABLE_SCAN";
                    differentIndexes.add(indexName);
                }
                sb.append("偏移分析:\n");
                sb.append(String.format("不同参数导致使用了不同的索引: %s\n",
                        String.join(", ", differentIndexes)));
                sb.append("可能原因: 数据分布不均、索引选择性差异、优化器成本估算变化\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            logger.warn("解析 PLAN_SHIFT 详情失败: {}", e.getMessage());
            sb.append("无法解析 PLAN_SHIFT 详情\n\n");
        }
    }

    private String formatTableStructures(List<TableStructure> tableStructures) {
        if (tableStructures == null || tableStructures.isEmpty()) {
            return "无表结构信息";
        }
        StringBuilder sb = new StringBuilder();
        for (TableStructure structure : tableStructures) {
            sb.append(String.format("=== 表: %s ===\n", structure.getTableName()));
            if (structure.getStatistics() != null) {
                TableStructure.TableStatistics stats = structure.getStatistics();
                sb.append(String.format("总行数: %d, 数据大小: %d bytes, 索引大小: %d bytes, 引擎: %s\n",
                        stats.getRows(), stats.getDataLength(), stats.getIndexLength(), stats.getEngine()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

}

