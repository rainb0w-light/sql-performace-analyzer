package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlanComparison;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 索引建议服务
 * 根据执行计划对比结果，分析是否需要添加索引，并给出具体建议
 */
@Service
public class IndexSuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(IndexSuggestionService.class);

    @Autowired
    private SqlExecutionPlanService sqlExecutionPlanService;

    // 匹配WHERE条件中的列名
    private static final Pattern WHERE_COLUMN_PATTERN = Pattern.compile(
        "(?i)(?:WHERE|AND|OR)\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s*[=<>!]+",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 根据执行计划对比结果生成索引建议
     * 
     * @param comparison 执行计划对比结果
     * @return 索引建议列表
     */
    public List<ExecutionPlanComparison.IndexSuggestion> generateSuggestions(
            ExecutionPlanComparison comparison) {
        logger.info("生成索引建议");

        List<ExecutionPlanComparison.IndexSuggestion> suggestions = new ArrayList<>();

        if (comparison.getComparisons() == null || comparison.getComparisons().isEmpty()) {
            return suggestions;
        }

        try {
            // 1. 分析所有场景，找出性能问题
            Map<String, TableAnalysis> tableAnalyses = analyzeTables(comparison);

            // 2. 为每个表生成索引建议
            for (Map.Entry<String, TableAnalysis> entry : tableAnalyses.entrySet()) {
                String tableName = entry.getKey();
                TableAnalysis analysis = entry.getValue();

                // 如果大部分场景都没有使用索引，或者扫描行数过多，建议添加索引
                if (shouldSuggestIndex(analysis)) {
                    ExecutionPlanComparison.IndexSuggestion suggestion = 
                        createIndexSuggestion(tableName, analysis, comparison);
                    if (suggestion != null) {
                        suggestions.add(suggestion);
                    }
                }
            }

            logger.info("生成了 {} 个索引建议", suggestions.size());
            return suggestions;

        } catch (Exception e) {
            logger.error("生成索引建议失败", e);
            return suggestions;
        }
    }

    /**
     * 分析所有场景中的表访问情况
     */
    private Map<String, TableAnalysis> analyzeTables(ExecutionPlanComparison comparison) {
        Map<String, TableAnalysis> analyses = new HashMap<>();

        for (ExecutionPlanComparison.ComparisonResult result : comparison.getComparisons()) {
            if (result.getTableName() == null) {
                continue;
            }

            String tableName = result.getTableName();
            TableAnalysis analysis = analyses.computeIfAbsent(tableName, k -> new TableAnalysis());

            analysis.totalScenarios++;
            
            if (Boolean.TRUE.equals(result.getUsesIndex())) {
                analysis.indexedScenarios++;
            } else {
                analysis.nonIndexedScenarios++;
            }

            if (result.getQueryCost() != null) {
                analysis.totalCost += result.getQueryCost();
                if (result.getQueryCost() > analysis.maxCost) {
                    analysis.maxCost = result.getQueryCost();
                }
                if (analysis.minCost == null || result.getQueryCost() < analysis.minCost) {
                    analysis.minCost = result.getQueryCost();
                }
            }

            if (result.getRowsExamined() != null) {
                analysis.totalRowsExamined += result.getRowsExamined();
                if (result.getRowsExamined() > analysis.maxRowsExamined) {
                    analysis.maxRowsExamined = result.getRowsExamined();
                }
            }

            // 收集WHERE条件中的列
            if (result.getFilledSql() != null) {
                Set<String> columns = extractWhereColumns(result.getFilledSql());
                analysis.whereColumns.addAll(columns);
            }
        }

        return analyses;
    }

    /**
     * 判断是否应该建议添加索引
     */
    private boolean shouldSuggestIndex(TableAnalysis analysis) {
        // 条件1：大部分场景都没有使用索引
        double nonIndexedRatio = (double) analysis.nonIndexedScenarios / analysis.totalScenarios;
        if (nonIndexedRatio > 0.5) {
            return true;
        }

        // 条件2：平均扫描行数过多（超过1000行）
        if (analysis.totalScenarios > 0) {
            long avgRows = analysis.totalRowsExamined / analysis.totalScenarios;
            if (avgRows > 1000) {
                return true;
            }
        }

        // 条件3：查询成本差异很大（说明某些场景性能很差）
        if (analysis.minCost != null && analysis.maxCost > 0) {
            double costRatio = analysis.maxCost / analysis.minCost;
            if (costRatio > 10) { // 最差场景比最好场景慢10倍以上
                return true;
            }
        }

        return false;
    }

    /**
     * 创建索引建议
     */
    private ExecutionPlanComparison.IndexSuggestion createIndexSuggestion(
            String tableName, TableAnalysis analysis, ExecutionPlanComparison comparison) {
        
        ExecutionPlanComparison.IndexSuggestion suggestion = 
            new ExecutionPlanComparison.IndexSuggestion();
        suggestion.setTableName(tableName);
        suggestion.setIndexType("INDEX");

        // 确定索引列：使用WHERE条件中出现最频繁的列
        List<String> indexColumns = determineIndexColumns(analysis);
        if (indexColumns.isEmpty()) {
            return null; // 无法确定索引列
        }

        suggestion.setColumns(indexColumns);

        // 计算预期性能提升
        calculateExpectedImprovement(suggestion, analysis, comparison);

        // 生成建议原因
        suggestion.setReason(generateReason(analysis, suggestion));

        return suggestion;
    }

    /**
     * 确定索引列
     */
    private List<String> determineIndexColumns(TableAnalysis analysis) {
        // 统计列出现频率
        Map<String, Integer> columnFrequency = new HashMap<>();
        for (String column : analysis.whereColumns) {
            // 移除表名前缀
            String colName = column;
            if (column.contains(".")) {
                colName = column.substring(column.lastIndexOf(".") + 1);
            }
            columnFrequency.put(colName.toLowerCase(), 
                               columnFrequency.getOrDefault(colName.toLowerCase(), 0) + 1);
        }

        // 按频率排序，取前3个
        return columnFrequency.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * 计算预期性能提升
     */
    private void calculateExpectedImprovement(
            ExecutionPlanComparison.IndexSuggestion suggestion,
            TableAnalysis analysis,
            ExecutionPlanComparison comparison) {
        
        // 使用最差场景的成本作为当前成本
        suggestion.setCurrentCost(analysis.maxCost);

        // 估算预期成本（假设添加索引后，成本降低到平均成本的50%）
        if (analysis.totalScenarios > 0) {
            double avgCost = analysis.totalCost / analysis.totalScenarios;
            double expectedCost = avgCost * 0.5; // 假设提升50%
            suggestion.setExpectedCost(expectedCost);

            if (analysis.maxCost > 0) {
                double improvement = ((analysis.maxCost - expectedCost) / analysis.maxCost) * 100;
                suggestion.setExpectedImprovement(Math.max(0, improvement));
            }
        }
    }

    /**
     * 生成建议原因
     */
    private String generateReason(TableAnalysis analysis, 
                                  ExecutionPlanComparison.IndexSuggestion suggestion) {
        StringBuilder reason = new StringBuilder();
        
        double nonIndexedRatio = (double) analysis.nonIndexedScenarios / analysis.totalScenarios;
        if (nonIndexedRatio > 0.5) {
            reason.append(String.format("在%d个测试场景中，有%d个场景未使用索引（%.1f%%）。", 
                                      analysis.totalScenarios, analysis.nonIndexedScenarios, 
                                      nonIndexedRatio * 100));
        }

        if (analysis.totalScenarios > 0) {
            long avgRows = analysis.totalRowsExamined / analysis.totalScenarios;
            if (avgRows > 1000) {
                reason.append(String.format("平均扫描行数：%d行，建议添加索引以提升查询性能。", avgRows));
            }
        }

        if (suggestion.getExpectedImprovement() != null && suggestion.getExpectedImprovement() > 0) {
            reason.append(String.format("预期性能提升：%.1f%%。", suggestion.getExpectedImprovement()));
        }

        return reason.toString();
    }

    /**
     * 从SQL中提取WHERE条件中的列名
     */
    private Set<String> extractWhereColumns(String sql) {
        Set<String> columns = new HashSet<>();
        
        if (sql == null || sql.trim().isEmpty()) {
            return columns;
        }

        Matcher matcher = WHERE_COLUMN_PATTERN.matcher(sql);
        while (matcher.find()) {
            String column = matcher.group(1);
            // 移除表名前缀
            if (column.contains(".")) {
                column = column.substring(column.lastIndexOf(".") + 1);
            }
            columns.add(column.toLowerCase());
        }

        return columns;
    }

    /**
     * 表分析数据类
     */
    private static class TableAnalysis {
        int totalScenarios = 0;
        int indexedScenarios = 0;
        int nonIndexedScenarios = 0;
        double totalCost = 0;
        Double minCost = null;
        double maxCost = 0;
        long totalRowsExamined = 0;
        long maxRowsExamined = 0;
        Set<String> whereColumns = new HashSet<>();
    }
}
