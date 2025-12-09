package com.example.sqlanalyzer.model;

import lombok.Data;

@Data
public class SqlAnalysisRequest {
    /**
     * SQL语句
     */
    private String sql;

    /**
     * 可选的数据库名称（如果与默认不同）
     */
    private String database;

    /**
     * 数据源名称（可选，如果不指定则使用默认数据源）
     */
    private String datasourceName;

    /**
     * 大模型名称（可选，如果不指定则使用默认模型）
     */
    private String llmName;
}
