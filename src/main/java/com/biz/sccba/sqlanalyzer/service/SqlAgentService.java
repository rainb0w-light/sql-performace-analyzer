package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.*;
import com.biz.sccba.sqlanalyzer.model.request.MultiSqlAgentRequest;
import com.biz.sccba.sqlanalyzer.model.request.MultiSqlAgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SQL Agent 编排层，只负责阶段串联与结果汇总。
 */
@Service
public class SqlAgentService {

    private static final Logger logger = LoggerFactory.getLogger(SqlAgentService.class);
    private final ObjectMapper objectMapper;
    @Autowired
    private SqlFillingService fillingService;
    @Autowired
    private ExecutionPlanServiceFacade executionPlanFacade;
    @Autowired
    private PlanAnalysisService planAnalysisService;
    @Autowired
    private IndexReportService indexReportService;

    public SqlAgentService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }


    /**
     * 多条 SQL 批量全流程，阶段4一次性生成报告。
     */
    public MultiSqlAgentResponse analyzeMultipleSqlsWithWorkflow(List<MultiSqlAgentRequest.SqlItem> sqlItems,
                                                               String datasourceName,
                                                               String llmName) throws AgentException {
        logger.info("开始批量 SQL 工作流分析 - SQL 数量: {}", sqlItems.size());

        List<SqlPlanAnalysisResult> allAnalysisResults = new ArrayList<>();
        String commonNamespace = null;

        for (int i = 0; i < sqlItems.size(); i++) {
            MultiSqlAgentRequest.SqlItem sqlItem = sqlItems.get(i);
            String sql = sqlItem.getSql();
            String mapperId = (sqlItem.getMapperId() == null || sqlItem.getMapperId().isBlank())
                    ? "unknown.sql_" + (i + 1)
                    : sqlItem.getMapperId();
            if (commonNamespace == null) {
                commonNamespace = mapperId.contains(".") ?
                        mapperId.substring(0, mapperId.lastIndexOf(".")) : mapperId;
            }

            try {
                SqlFillingRecord fillingRecord = fillingService.fillSqlWithDataDistribution(sql, mapperId, datasourceName, llmName);
                List<FilledSqlScenario> scenarios = parseFillingResult(fillingRecord);
                List<String> scenarioNames = scenarios.stream()
                        .map(FilledSqlScenario::getScenarioName)
                        .collect(java.util.stream.Collectors.toList());
                List<String> filledSqls = scenarios.stream()
                        .map(FilledSqlScenario::getFilledSql)
                        .collect(java.util.stream.Collectors.toList());

                var planRecords = executionPlanFacade.generateExecutionPlans(fillingRecord, scenarioNames, filledSqls, datasourceName);
                if (planRecords.isEmpty()) {
                    throw new AgentException(AgentErrorCode.EXECUTION_PLAN_FAILED, "未能生成执行计划记录");
                }

                var analysisResults = planAnalysisService.analyzeExecutionPlans(mapperId, sql, planRecords);
                if (analysisResults.isEmpty()) {
                    throw new AgentException(AgentErrorCode.PLAN_ANALYSIS_FAILED, "未能生成分析结果");
                }

                allAnalysisResults.addAll(analysisResults);
                logger.info("SQL {} 分析完成，分析结果数: {}", i + 1, analysisResults.size());
            } catch (AgentException e) {
                logger.error("SQL {} 分析失败，继续处理其他 SQL: {}", i + 1, e.getMessage());
                // 继续处理其他 SQL，不中断整个流程
            } catch (Exception e) {
                logger.error("SQL {} 分析失败，继续处理其他 SQL", i + 1, e);
                // 继续处理其他 SQL，不中断整个流程
            }
        }

        if (allAnalysisResults.isEmpty()) {
            throw new AgentException(AgentErrorCode.PLAN_ANALYSIS_FAILED, "所有 SQL 都未能生成分析结果");
        }

        String namespace = Objects.requireNonNullElse(commonNamespace, "unknown");
        IndexOptimizationReport report = indexReportService.generateIndexOptimizationReport(
                namespace, datasourceName, llmName, allAnalysisResults);
        
        MultiSqlAgentResponse response = new MultiSqlAgentResponse();
        response.setReportContent(report.getReportContent());
        
        logger.info("批量 SQL 工作流分析完成 - SQL 数量: {}, 报告ID: {}", sqlItems.size(), report.getId());
        return response;
    }

    private List<FilledSqlScenario> parseFillingResult(SqlFillingRecord fillingRecord) throws AgentException {
        try {
            SqlFillingResult fillingResult = objectMapper.readValue(
                    fillingRecord.getFillingResultJson(), SqlFillingResult.class);
            if (fillingResult.getScenarios() == null || fillingResult.getScenarios().isEmpty()) {
                throw new AgentException(AgentErrorCode.DATA_FILL_FAILED, "填充结果中没有场景数据");
            }
            return fillingResult.getScenarios();
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_FAILED, "解析填充结果失败", e);
        }
    }
}
