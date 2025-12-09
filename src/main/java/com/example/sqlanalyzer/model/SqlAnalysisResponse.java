package com.example.sqlanalyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SqlAnalysisResponse {
    @JsonProperty("sql")
    private String sql;
    
    @JsonProperty("executionPlan")
    private ExecutionPlan executionPlan;
    
    @JsonProperty("tableStructures")
    private List<TableStructure> tableStructures;
    
    @JsonProperty("analysisResult")
    private String analysisResult; // DeepSeek分析结果（兼容字段，保留原有分析结果）
    
    @JsonProperty("mysqlAnalysisResult")
    private String mysqlAnalysisResult; // MySQL InnoDB分析结果
    
    @JsonProperty("goldenDbAnalysisResult")
    private String goldenDbAnalysisResult; // GoldenDB分布式分析结果
    
    @JsonProperty("report")
    private String report; // Markdown格式的报告

    // Getters and Setters
    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public ExecutionPlan getExecutionPlan() {
        return executionPlan;
    }

    public void setExecutionPlan(ExecutionPlan executionPlan) {
        this.executionPlan = executionPlan;
    }

    public List<TableStructure> getTableStructures() {
        return tableStructures;
    }

    public void setTableStructures(List<TableStructure> tableStructures) {
        this.tableStructures = tableStructures;
    }

    public String getAnalysisResult() {
        return analysisResult;
    }

    public void setAnalysisResult(String analysisResult) {
        this.analysisResult = analysisResult;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public String getMysqlAnalysisResult() {
        return mysqlAnalysisResult;
    }

    public void setMysqlAnalysisResult(String mysqlAnalysisResult) {
        this.mysqlAnalysisResult = mysqlAnalysisResult;
    }

    public String getGoldenDbAnalysisResult() {
        return goldenDbAnalysisResult;
    }

    public void setGoldenDbAnalysisResult(String goldenDbAnalysisResult) {
        this.goldenDbAnalysisResult = goldenDbAnalysisResult;
    }
}

