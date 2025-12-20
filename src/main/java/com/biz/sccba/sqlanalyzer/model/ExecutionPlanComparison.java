package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 执行计划对比结果
 * 用于对比不同采样数据下的执行计划表现
 */
@Data
public class ExecutionPlanComparison {

    /**
     * 原始SQL
     */
    private String originalSql;

    /**
     * 数据源名称
     */
    private String datasourceName;

    /**
     * 对比结果列表
     */
    private List<ComparisonResult> comparisons;

    /**
     * 最佳执行计划（成本最低）
     */
    private ComparisonResult bestPlan;

    /**
     * 最差执行计划（成本最高）
     */
    private ComparisonResult worstPlan;

    /**
     * 是否需要索引建议
     */
    private Boolean needsIndexSuggestion;

    /**
     * 索引建议列表
     */
    private List<IndexSuggestion> indexSuggestions;

    @Data
    public static class ComparisonResult {
        /**
         * 采样场景描述（如：最小值、最大值、中位数等）
         */
        private String scenario;

        /**
         * 填充后的SQL
         */
        private String filledSql;

        /**
         * 使用的采样值（Map格式：参数名 -> 值）
         */
        private Map<String, Object> sampleValues;

        /**
         * 表名
         */
        private String tableName;

        /**
         * 执行计划
         */
        private ExecutionPlan executionPlan;

        /**
         * 查询成本
         */
        private Double queryCost;

        /**
         * 扫描行数
         */
        private Long rowsExamined;

        /**
         * 是否使用索引
         */
        private Boolean usesIndex;

        /**
         * 使用的索引名称
         */
        private String indexName;

        /**
         * 访问类型（如：ALL, index, range等）
         */
        private String accessType;
    }

    @Data
    public static class IndexSuggestion {
        /**
         * 表名
         */
        private String tableName;

        /**
         * 建议的索引列（可以是多列）
         */
        private List<String> columns;

        /**
         * 索引类型（如：INDEX, UNIQUE等）
         */
        private String indexType;

        /**
         * 建议原因
         */
        private String reason;

        /**
         * 预期性能提升（百分比）
         */
        private Double expectedImprovement;

        /**
         * 当前成本
         */
        private Double currentCost;

        /**
         * 预期成本
         */
        private Double expectedCost;
    }
}
