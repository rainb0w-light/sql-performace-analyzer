package com.example.sqlanalyzer.model;

import lombok.Data;

@Data
public class ExecutionPlan {
    /**
     * 执行计划的JSON格式字符串
     */
    private String jsonPlan;

    /**
     * 原始JSON字符串
     */
    private String rawJson;

    /**
     * 查询块信息
     */
    private QueryBlock queryBlock;

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
     * 连接类型
     */
    private String joinType;

    /**
     * 是否使用临时表
     */
    private Boolean usesTemporary;

    /**
     * 是否使用文件排序
     */
    private Boolean usesFilesort;

    @Data
    public static class QueryBlock {
        private Integer selectId;
        private CostInfo costInfo;
        private TableInfo table;
    }

    @Data
    public static class CostInfo {
        private String queryCost;
        private String readCost;
    }

    @Data
    public static class TableInfo {
        private String tableName;
        private String accessType;
        private String key;
        private Long rowsExaminedPerScan;
        private Long rowsProducedPerJoin;
        private String[] usedColumns;
    }
}
