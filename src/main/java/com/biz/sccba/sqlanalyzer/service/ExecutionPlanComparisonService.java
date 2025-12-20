package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.ExecutionPlanComparison;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 执行计划对比分析服务
 * 对比不同采样数据下的执行计划表现，并生成索引建议
 */
@Service
public class ExecutionPlanComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionPlanComparisonService.class);

    @Autowired
    private DistributionBasedSqlFillerService sqlFillerService;

    @Autowired
    private SqlExecutionPlanService sqlExecutionPlanService;

    @Autowired
    private IndexSuggestionService indexSuggestionService;

    /**
     * 对比分析SQL在不同场景下的执行计划
     * 
     * @param sql 原始SQL（包含占位符）
     * @param datasourceName 数据源名称
     * @return 执行计划对比结果
     */
    public ExecutionPlanComparison compareExecutionPlans(String sql, String datasourceName) {
        logger.info("开始对比执行计划: sql={}, datasource={}", sql, datasourceName);

        ExecutionPlanComparison comparison = new ExecutionPlanComparison();
        comparison.setOriginalSql(sql);
        comparison.setDatasourceName(datasourceName);

        try {
            // 1. 生成多个SQL场景
            List<DistributionBasedSqlFillerService.SqlScenario> scenarios = 
                sqlFillerService.generateSqlScenarios(sql, datasourceName);

            if (scenarios.isEmpty()) {
                logger.warn("无法生成SQL场景");
                return comparison;
            }

            // 2. 为每个场景获取执行计划
            List<ExecutionPlanComparison.ComparisonResult> results = new ArrayList<>();
            
            for (DistributionBasedSqlFillerService.SqlScenario scenario : scenarios) {
                try {
                    ExecutionPlanComparison.ComparisonResult result = analyzeScenario(
                        scenario, datasourceName);
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    logger.warn("分析场景失败: scenario={}, error={}", 
                               scenario.getScenarioName(), e.getMessage());
                }
            }

            comparison.setComparisons(results);

            // 3. 找出最佳和最差执行计划
            if (!results.isEmpty()) {
                ExecutionPlanComparison.ComparisonResult best = results.stream()
                    .filter(r -> r.getQueryCost() != null)
                    .min(Comparator.comparing(ExecutionPlanComparison.ComparisonResult::getQueryCost))
                    .orElse(null);
                
                ExecutionPlanComparison.ComparisonResult worst = results.stream()
                    .filter(r -> r.getQueryCost() != null)
                    .max(Comparator.comparing(ExecutionPlanComparison.ComparisonResult::getQueryCost))
                    .orElse(null);

                comparison.setBestPlan(best);
                comparison.setWorstPlan(worst);
            }

            // 4. 生成索引建议
            List<ExecutionPlanComparison.IndexSuggestion> suggestions = 
                indexSuggestionService.generateSuggestions(comparison);
            comparison.setIndexSuggestions(suggestions);
            comparison.setNeedsIndexSuggestion(!suggestions.isEmpty());

            logger.info("执行计划对比完成，共分析 {} 个场景，生成 {} 个索引建议", 
                       results.size(), suggestions.size());

            return comparison;

        } catch (Exception e) {
            logger.error("对比执行计划失败", e);
            throw new RuntimeException("对比执行计划失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分析单个场景
     */
    private ExecutionPlanComparison.ComparisonResult analyzeScenario(
            DistributionBasedSqlFillerService.SqlScenario scenario, String datasourceName) {
        
        ExecutionPlanComparison.ComparisonResult result = new ExecutionPlanComparison.ComparisonResult();
        result.setScenario(scenario.getScenarioName());
        result.setFilledSql(scenario.getFilledSql());
        result.setSampleValues(scenario.getSampleValues());

        try {
            // 获取执行计划
            ExecutionPlan plan = sqlExecutionPlanService.getExecutionPlan(
                scenario.getFilledSql(), datasourceName);

            if (plan == null) {
                logger.warn("无法获取执行计划: scenario={}", scenario.getScenarioName());
                return null;
            }

            result.setExecutionPlan(plan);

            // 提取关键指标
            extractMetrics(plan, result);

            return result;

        } catch (Exception e) {
            logger.warn("分析场景执行计划失败: scenario={}, error={}", 
                       scenario.getScenarioName(), e.getMessage());
            return null;
        }
    }

    /**
     * 从执行计划中提取关键指标
     */
    private void extractMetrics(ExecutionPlan plan, ExecutionPlanComparison.ComparisonResult result) {
        // 从原始JSON中提取查询成本
        if (plan.getRawJson() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = 
                    new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(plan.getRawJson());
                
                // 提取查询成本
                com.fasterxml.jackson.databind.JsonNode costInfo = 
                    jsonNode.path("query_block").path("cost_info");
                if (costInfo.has("query_cost")) {
                    String costStr = costInfo.get("query_cost").asText();
                    try {
                        result.setQueryCost(Double.parseDouble(costStr));
                    } catch (NumberFormatException e) {
                        // 忽略解析错误
                    }
                }
            } catch (Exception e) {
                logger.debug("解析执行计划JSON失败: {}", e.getMessage());
            }
        }

        // 从QueryBlock中提取信息
        if (plan.getQueryBlock() != null) {
            ExecutionPlan.QueryBlock queryBlock = plan.getQueryBlock();
            
            if (queryBlock.getCostInfo() != null) {
                String costStr = queryBlock.getCostInfo().getQueryCost();
                if (costStr != null) {
                    try {
                        result.setQueryCost(Double.parseDouble(costStr));
                    } catch (NumberFormatException e) {
                        // 忽略解析错误
                    }
                }
            }

            if (queryBlock.getTable() != null) {
                ExecutionPlan.TableInfo tableInfo = queryBlock.getTable();
                result.setTableName(tableInfo.getTableName());
                result.setAccessType(tableInfo.getAccessType());
                result.setIndexName(tableInfo.getKey());
                result.setRowsExamined(tableInfo.getRowsExaminedPerScan());
                
                // 判断是否使用索引
                result.setUsesIndex(tableInfo.getKey() != null && !tableInfo.getKey().isEmpty());
            }
        }

        // 从plan对象中提取
        if (result.getQueryCost() == null && plan.getQueryCost() != null) {
            result.setQueryCost(plan.getQueryCost());
        }
        if (result.getRowsExamined() == null && plan.getRowsExamined() != null) {
            result.setRowsExamined(plan.getRowsExamined());
        }
        if (result.getUsesIndex() == null && plan.getUsesIndex() != null) {
            result.setUsesIndex(plan.getUsesIndex());
        }
        if (result.getIndexName() == null && plan.getIndexName() != null) {
            result.setIndexName(plan.getIndexName());
        }
    }
}
